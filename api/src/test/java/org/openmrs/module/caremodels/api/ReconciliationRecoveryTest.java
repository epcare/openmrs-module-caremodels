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
        when(dao.getMaximumPatientId()).thenReturn(2); when(dao.getPatientIdsAfter(0, 2, 1)).thenReturn(Collections.singletonList(1));
        when(dao.getPatientIdsAfter(1, 2, 1)).thenReturn(Collections.singletonList(2)); when(dao.getPatientIdsAfter(2, 2, 1)).thenReturn(Collections.emptyList());
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
		verify(enrollment, times(2)).applyRuleEnrollment(anyInt(), eq(3), eq("eligible"),
		    eq(EnrollmentRuleEngine.Action.AUTO_ENROLL), anyString(), any());
	}

	@Test
	public void failedPersistenceMustNotAdvanceTheCheckpointAndRetryMustUseSameSource() {
		doThrow(new APIException("temporary failure")).doReturn(null).when(enrollment)
		        .applyRuleEnrollment(anyInt(), eq(3), anyString(), any(), anyString(), any());
		try {
			recovery.reconcileNextBatch(1);
			fail();
		}
		catch (APIException expected) {}
		assertEquals("{}", cursor.getPropertyValue());
		recovery.reconcileNextBatch(1);
		org.mockito.ArgumentCaptor<String> source = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(enrollment, times(2)).applyRuleEnrollment(eq(1), eq(3), eq("eligible"), any(), source.capture(), any());
		assertEquals(source.getAllValues().get(0), source.getAllValues().get(1));
	}

	@Test public void unavailableEvidenceMustRetryWhileKnownAbsenceMustAllowProgress() throws Exception {
        when(evidence.create(anyInt(), any(), any())).thenReturn((type, alias) -> RuleEvidence.unknown());
        try { recovery.reconcileNextBatch(1); fail(); } catch (APIException expected) { }
        assertEquals("{}", cursor.getPropertyValue()); verifyNoInteractions(enrollment);
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
		verify(dao, times(2)).getPatientIdsAfter(0, 2, 1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectUnboundedBatches() {
		recovery.reconcileNextBatch(101);
	}
}
