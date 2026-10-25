package org.openmrs.module.caremodels.api;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.*;
import org.mockito.MockedStatic;
import org.openmrs.GlobalProperty;
import org.openmrs.api.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.db.*;
import org.openmrs.module.caremodels.api.impl.CareModelReconciliationServiceImpl;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.event.EnrollmentRuleEvidenceFactory;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.rule.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class ReconciliationRecoveryTest {
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private MockedStatic<Context> context;
	
	private CareModelDAO dao;
	
	private CareModelMemberDAO members;
	
	private CareModelEnrollmentService enrollment;
	
	private AdministrationService admin;
	
	private GlobalProperty cursor;
	
	private CareModel model;
	
	private EnrollmentRuleEvidenceFactory evidence;
	
	private CareModelReconciliationServiceImpl recovery;
	
	@Before public void setup() throws Exception {
        context = mockStatic(Context.class); admin = mock(AdministrationService.class); context.when(Context::getAdministrationService).thenReturn(admin);
        dao = mock(CareModelDAO.class); members = mock(CareModelMemberDAO.class); enrollment = mock(CareModelEnrollmentService.class); evidence = mock(EnrollmentRuleEvidenceFactory.class);
        cursor = new GlobalProperty("caremodels.reconciliation.cursor", "{}"); when(dao.getReconciliationCursorForUpdate()).thenReturn(cursor);
        StorageEnvelope envelope = new StorageEnvelope();
        envelope.saveDraft(mapper.readTree("{\"enrollment\":{\"enabled\":true,\"rules\":[{\"id\":\"eligible\",\"action\":\"AUTO_ENROLL\",\"expression\":{\"type\":\"observation\",\"alias\":\"fact\",\"operator\":\"EXISTS\"}}]}}"), 0).publishDraft("1.0.0", 1, new Date());
        model = new CareModel(); model.setId(3); model.setStatus(CareModel.CareModelStatus.PUBLISHED); model.setConfiguration(envelope.toJson(mapper));
        when(dao.getEnrollmentModelsAfter(0, 1)).thenReturn(Collections.singletonList(model)); when(dao.getCareModelById(3)).thenReturn(model);
        when(dao.getMaximumPatientId()).thenReturn(2);
        when(admin.getGlobalProperty(eq("caremodels.reconciliation.activityWindowDays"), anyString())).thenReturn("90");
        when(dao.getActivePatientIdsAfter(eq(0), eq(2), any(), eq(1))).thenReturn(Collections.singletonList(1));
        when(dao.getActivePatientIdsAfter(eq(1), eq(2), any(), eq(1))).thenReturn(Collections.singletonList(2));
        when(dao.getActivePatientIdsAfter(eq(2), eq(2), any(), eq(1))).thenReturn(Collections.emptyList());
        when(members.getNextEpisodeNumber(anyInt(), eq(3))).thenReturn(1);
        when(evidence.create(anyInt(), any(), any())).thenReturn((type, alias) -> RuleEvidence.present(true));
        recovery = new CareModelReconciliationServiceImpl(); recovery.setCareModelDAO(dao); recovery.setMemberDAO(members); recovery.setEnrollmentService(enrollment); recovery.setEvidenceFactory(evidence);
    }
	
	@After
	public void cleanup() {
		if (context != null)
			context.close();
	}
	
	@Test
	public void shouldResumeBoundedBatchesFromPersistedCoreCheckpoint() throws Exception {
		assertEquals(1, recovery.reconcileNextBatch(1));
		assertEquals(1, mapper.readTree(cursor.getPropertyValue()).path("patientId").asInt());
		String evaluationTime = mapper.readTree(cursor.getPropertyValue()).path("asOf").asText();
		assertEquals(1, recovery.reconcileNextBatch(1));
		assertEquals(2, mapper.readTree(cursor.getPropertyValue()).path("patientId").asInt());
		assertEquals(evaluationTime, mapper.readTree(cursor.getPropertyValue()).path("asOf").asText());
		assertEquals(0, recovery.reconcileNextBatch(1));
		assertTrue(mapper.readTree(cursor.getPropertyValue()).path("completed").asBoolean());
		verify(enrollment, times(2)).applyBackfillEnrollment(anyInt(), eq(3), eq("eligible"),
		    eq(EnrollmentRuleEngine.Action.AUTO_ENROLL), anyString(), any());
	}
	
	@Test
	public void failedPersistenceMustNotAdvanceTheCheckpointAndRetryMustUseSameSource() throws Exception {
		doThrow(new APIException("temporary failure")).doReturn(null).when(enrollment)
		        .applyBackfillEnrollment(anyInt(), eq(3), anyString(), any(), anyString(), any());
		// The failed patient is logged and excluded from the cursor (patientId stays at the start
		// of the model page); the batch itself does not abort.
		assertEquals(0, recovery.reconcileNextBatch(1));
		assertEquals(0, mapper.readTree(cursor.getPropertyValue()).path("patientId").asInt());
		recovery.reconcileNextBatch(1);
		assertEquals(1, mapper.readTree(cursor.getPropertyValue()).path("patientId").asInt());
		org.mockito.ArgumentCaptor<String> source = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(enrollment, times(2)).applyBackfillEnrollment(eq(1), eq(3), eq("eligible"), any(), source.capture(), any());
		assertEquals(source.getAllValues().get(0), source.getAllValues().get(1));
	}
	
	@Test public void unavailableEvidenceMustRetryWhileKnownAbsenceMustAllowProgress() throws Exception {
        when(evidence.create(anyInt(), any(), any())).thenReturn((type, alias) -> RuleEvidence.unknown());
        // Unavailable evidence stops the batch; the checkpoint keeps the model page at its first
        // patient so the next run retries exactly the same page.
        assertEquals(0, recovery.reconcileNextBatch(1));
        assertEquals(0, mapper.readTree(cursor.getPropertyValue()).path("patientId").asInt());
        verifyNoInteractions(enrollment);
        when(evidence.create(anyInt(), any(), any())).thenReturn((type, alias) -> RuleEvidence.absent());
        assertEquals(1, recovery.reconcileNextBatch(1)); verifyNoInteractions(enrollment);
    }
	
	@Test
	public void changedPublicationMustRestartTheModelScan() throws Exception {
		recovery.reconcileNextBatch(1);
		StorageEnvelope envelope = StorageEnvelope.fromJson(mapper, model.getConfiguration());
		com.fasterxml.jackson.databind.node.ObjectNode definition = (com.fasterxml.jackson.databind.node.ObjectNode) envelope
		        .getPublished().getDefinition().deepCopy();
		definition.put("testRevision", 2);
		envelope.saveDraft(definition, 2).publishDraft("2.0.0", 3, new Date());
		model.setConfiguration(envelope.toJson(mapper));
		recovery.reconcileNextBatch(1);
		assertEquals(1, mapper.readTree(cursor.getPropertyValue()).path("patientId").asInt());
		verify(dao, times(2)).getActivePatientIdsAfter(eq(0), eq(2), any(), eq(1));
	}
	
	@Test
	public void disabledActivityWindowMustFallBackToTheFullPopulationScan() throws Exception {
		when(admin.getGlobalProperty(eq("caremodels.reconciliation.activityWindowDays"), anyString())).thenReturn("0");
		when(dao.getPatientIdsAfter(0, 2, 1)).thenReturn(Collections.singletonList(1));
		assertEquals(1, recovery.reconcileNextBatch(1));
		assertEquals(1, mapper.readTree(cursor.getPropertyValue()).path("patientId").asInt());
		verify(dao).getPatientIdsAfter(0, 2, 1);
		verify(dao, never()).getActivePatientIdsAfter(anyInt(), anyInt(), any(), anyInt());
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectUnboundedBatches() {
		recovery.reconcileNextBatch(101);
	}
	
	@Test public void knownIneligibilityMustFlagAutomaticMembershipButProtectManualMembership() throws Exception {
        org.openmrs.module.caremodels.model.CareModelMember current = new org.openmrs.module.caremodels.model.CareModelMember();
        current.setEnrollmentSource("BACKFILL"); current.setSourceUuid("source");
        when(members.getOpenMember(1, 3)).thenReturn(current);
        when(evidence.create(anyInt(), any(), any())).thenReturn((type, alias) -> RuleEvidence.absent());
        recovery.reconcileNextBatch(1);
        verify(enrollment).flagEnrollmentsForReview(eq(1), eq("source"), anyString(), any());
        clearInvocations(enrollment);
        cursor.setPropertyValue("{}"); current.setEnrollmentSource("MANUAL");
        recovery.reconcileNextBatch(1);
        verifyNoInteractions(enrollment);
    }
	
	@Test
	public void committedCreationMustNotChangeReplayIdentityButClosedEpisodeMustAllowANewPeriod() throws Exception {
		recovery.reconcileNextBatch(1);
		org.mockito.ArgumentCaptor<String> source = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(enrollment).applyBackfillEnrollment(eq(1), eq(3), anyString(), any(), source.capture(), any());
		String original = source.getValue();
		org.openmrs.module.caremodels.model.CareModelMember episode = new org.openmrs.module.caremodels.model.CareModelMember();
		episode.setEpisodeNumber(1);
		episode.setDateEnrolled(new Date(100));
		when(members.getMembersByPatientAndModel(1, 3)).thenReturn(Collections.singletonList(episode));
		when(members.getNextEpisodeNumber(1, 3)).thenReturn(2);
		cursor.setPropertyValue("{}");
		recovery.reconcileNextBatch(1);
		verify(enrollment, times(2)).applyBackfillEnrollment(eq(1), eq(3), anyString(), any(), source.capture(), any());
		assertEquals(original, source.getValue());
		episode.setDateCompleted(new Date(200));
		cursor.setPropertyValue("{}");
		recovery.reconcileNextBatch(1);
		verify(enrollment, times(3)).applyBackfillEnrollment(eq(1), eq(3), anyString(), any(), source.capture(), any());
		assertNotEquals(original, source.getValue());
	}
	
	@Test public void adminRecoveryMustRejectUnavailableEvidenceToo() throws Exception {
        recovery.setReconciliationDAO((after, limit) -> Collections.singletonList(1));
        when(dao.getPublishedCareModelsForEnrollment()).thenReturn(Collections.singletonList(model));
        when(evidence.create(anyInt(), any(), any())).thenReturn((type, alias) -> RuleEvidence.unknown());
        try { recovery.reconcile(0, 1, new Date()); fail("Must fail instead of returning a cursor past unavailable evidence"); }
        catch (APIException expected) { }
        verifyNoInteractions(enrollment);
    }
	
	@Test public void adminCountersMustExcludeReplaysAndNoopMemberships() throws Exception {
        recovery.setReconciliationDAO((after, limit) -> Collections.singletonList(1));
        when(dao.getPublishedCareModelsForEnrollment()).thenReturn(Collections.singletonList(model));
        org.openmrs.module.caremodels.model.CareModelMember member = new org.openmrs.module.caremodels.model.CareModelMember();
        member.setStatus(org.openmrs.module.caremodels.model.CareModelMember.MemberStatus.ACTIVE);
        when(members.getOpenMember(1, 3)).thenReturn(member);
        when(enrollment.applyBackfillEnrollment(anyInt(), anyInt(), anyString(), any(), anyString(), any())).thenReturn(member);
        assertEquals(0, recovery.reconcile(0, 1, new Date()).getAppliedDecisions());
        when(members.getOpenMember(1, 3)).thenReturn(null);
        when(members.getMemberBySource(anyInt(), anyInt(), anyString())).thenReturn(member);
        assertEquals(0, recovery.reconcile(0, 1, new Date()).getAppliedDecisions());
    }
}
