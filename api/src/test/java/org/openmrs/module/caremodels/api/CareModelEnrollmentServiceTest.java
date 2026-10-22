/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIException;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.api.db.CareModelMemberDAO;
import org.openmrs.module.caremodels.api.impl.CareModelEnrollmentServiceImpl;
import org.openmrs.module.caremodels.api.impl.EnrollmentActorProvider;
import org.openmrs.module.caremodels.api.impl.EnrollmentCommandAuthorizer;
import org.openmrs.module.caremodels.config.JsonSchemaValidator;
import org.openmrs.module.caremodels.config.JsonSchemaValidator.SchemaType;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.dto.CareModelRef;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.model.CareModelMember.MemberStatus;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Action;
import org.openmrs.module.caremodels.security.CareModelsPrivileges;

public class CareModelEnrollmentServiceTest {

	private static final Date NOW = new Date(1761033600000L);

	private final ObjectMapper mapper = new ObjectMapper();

	private CareModelEnrollmentServiceImpl service;

	private FakeCareModelDAO modelDAO;

	private FakeMemberDAO memberDAO;

	@BeforeClass
	public static void configureApplicationDataDirectory() {
		System.setProperty("OPENMRS_APPLICATION_DATA_DIRECTORY", System.getProperty("java.io.tmpdir"));
	}

	@Before
	public void setup() throws Exception {
		modelDAO = new FakeCareModelDAO(model(true));
		memberDAO = new FakeMemberDAO();
		service = new CareModelEnrollmentServiceImpl();
		service.setCareModelDAO(modelDAO);
		service.setMemberDAO(memberDAO);
		service.setObjectMapper(mapper);
		service.setActorProvider(new EnrollmentActorProvider() {

			@Override
			public String getCurrentActor() {
				return "actor-uuid";
			}
		});
		service.setCommandAuthorizer(new EnrollmentCommandAuthorizer() {

			@Override
			public void authorize(Integer patientId, CareModel model) {
				// Unit tests exercise the command semantics independently of OpenMRS Context.
			}
		});
	}

	@Test
	public void shouldCreateActiveAutomaticEnrollmentWithPublishedProvenance() throws Exception {
		CareModelMember member = service.applyRuleEnrollment(7, 3, "auto-rule", Action.AUTO_ENROLL, "event-1", NOW);
		assertEquals(MemberStatus.ACTIVE, member.getStatus());
		assertEquals("AUTO", member.getEnrollmentSource());
		assertEquals(Integer.valueOf(1), member.getEpisodeNumber());
		assertEquals(NOW, member.getDateEnrolled());
		JsonNode data = mapper.readTree(member.getMembershipData());
		assertEquals("2.1.0", data.path("enrollment").path("definitionVersion").asText());
		assertEquals("auto-rule", data.path("enrollment").path("ruleId").asText());
		assertEquals("AUTO", data.path("enrollment").path("trigger").path("type").asText());
		assertEquals("ENROLLED", data.path("history").get(0).path("type").asText());
		assertTrue(new JsonSchemaValidator(mapper).validate(data, SchemaType.MEMBERSHIP_DATA).isValid());
		assertEquals(1, modelDAO.lockCount);
	}

	@Test
	public void shouldReplaySameSourceWithoutCreatingAnotherEpisode() {
		CareModelMember first = service.applyRuleEnrollment(7, 3, "confirm-rule", Action.PROPOSE_CONFIRMATION, "event-2",
		    NOW);
		CareModelMember replay = service.applyRuleEnrollment(7, 3, "confirm-rule", Action.PROPOSE_CONFIRMATION, "event-2",
		    new Date(NOW.getTime() + 1000));
		assertSame(first, replay);
		assertEquals(MemberStatus.PENDING, first.getStatus());
		assertEquals(1, memberDAO.members.size());
	}

	@Test
	public void shouldConfirmPendingProposalIdempotently() throws Exception {
		CareModelMember proposal = service.applyRuleEnrollment(7, 3, "confirm-rule", Action.PROPOSE_CONFIRMATION, "event-3",
		    NOW);
		Date confirmedAt = new Date(NOW.getTime() + 5000);
		CareModelMember confirmed = service.confirmEnrollment(proposal.getUuid(), "Patient accepted", confirmedAt);
		assertEquals(MemberStatus.ACTIVE, confirmed.getStatus());
		assertEquals(confirmedAt, confirmed.getDateEnrolled());
		JsonNode data = mapper.readTree(confirmed.getMembershipData());
		assertEquals(confirmedAt.toInstant().toString().substring(0, 19),
		    data.path("enrollment").path("confirmation").path("confirmedAt").asText().substring(0, 19));
		assertEquals("ENROLLED", data.path("history").get(1).path("type").asText());
		assertSame(confirmed, service.confirmEnrollment(proposal.getUuid(), "Patient accepted", confirmedAt));
	}

	@Test
	public void shouldDeclinePendingProposalIdempotently() throws Exception {
		CareModelMember proposal = service.applyRuleEnrollment(7, 3, "confirm-rule", Action.PROPOSE_CONFIRMATION, "event-4",
		    NOW);
		CareModelMember declined = service.declineEnrollment(proposal.getUuid(), "Patient declined", NOW);
		assertEquals(MemberStatus.DECLINED, declined.getStatus());
		assertEquals(NOW, declined.getDateCompleted());
		assertEquals("DECLINED", mapper.readTree(declined.getMembershipData()).path("history").get(1).path("type").asText());
		assertSame(declined, service.declineEnrollment(proposal.getUuid(), "Patient declined", NOW));
	}

	@Test
	public void shouldCreateOneManualEpisodeWhenPublishedDefinitionAllowsIt() throws Exception {
		CareModelMember first = service.enrollManually(8, 3, "Clinician enrolled patient", NOW);
		CareModelMember replay = service.enrollManually(8, 3, "Clinician enrolled patient", NOW);
		assertSame(first, replay);
		assertEquals(MemberStatus.ACTIVE, first.getStatus());
		assertEquals("MANUAL", first.getEnrollmentSource());
		assertEquals(1, memberDAO.members.size());
		JsonNode data = mapper.readTree(first.getMembershipData());
		assertEquals("Clinician enrolled patient", data.path("history").get(0).path("reason").asText());
	}

	@Test
	public void shouldPromoteExistingProposalInsteadOfCreatingManualDuplicate() throws Exception {
		CareModelMember proposal = service.applyRuleEnrollment(9, 3, "confirm-rule", Action.PROPOSE_CONFIRMATION, "event-5",
		    NOW);
		CareModelMember enrolled = service.enrollManually(9, 3, "Clinician decision", NOW);
		assertSame(proposal, enrolled);
		assertEquals(MemberStatus.ACTIVE, enrolled.getStatus());
		assertEquals("MANUAL", enrolled.getEnrollmentSource());
		assertEquals(1, memberDAO.members.size());
		assertEquals(2, mapper.readTree(enrolled.getMembershipData()).path("history").size());
	}

	@Test
	public void shouldFlagCorrectedEvidenceForReviewIdempotentlyWithoutChangingStatus() throws Exception {
		CareModelMember member = service.applyRuleEnrollment(7, 3, "auto-rule", Action.AUTO_ENROLL, "event-review", NOW);
		Date correctedAt = new Date(NOW.getTime() + 1000);

		assertEquals(1, service.flagEnrollmentsForReview(7, "event-review", "Source observation was corrected", correctedAt));
		assertEquals(0, service.flagEnrollmentsForReview(7, "event-review", "Source observation was corrected", correctedAt));

		assertEquals(MemberStatus.ACTIVE, member.getStatus());
		JsonNode history = mapper.readTree(member.getMembershipData()).path("history");
		assertEquals(2, history.size());
		assertEquals("FLAGGED_FOR_REVIEW", history.get(1).path("type").asText());
		assertEquals("event-review", history.get(1).path("sourceUuid").asText());
		assertEquals("actor-uuid", history.get(1).path("actor").asText());
	}

	@Test
	public void shouldSuspendAndResumeWithAuditedHistory() throws Exception {
		CareModelMember member = service.enrollManually(8, 3, "Initial decision", NOW);
		Date suspendedAt = new Date(NOW.getTime() + 1000);
		service.transitionEnrollment(member.getUuid(), MemberStatus.SUSPENDED, "Treatment paused", suspendedAt);
		assertEquals(MemberStatus.SUSPENDED, member.getStatus());

		Date resumedAt = new Date(NOW.getTime() + 2000);
		service.transitionEnrollment(member.getUuid(), MemberStatus.ACTIVE, "Treatment resumed", resumedAt);
		assertEquals(MemberStatus.ACTIVE, member.getStatus());
		assertEquals(null, member.getDateCompleted());
		JsonNode history = mapper.readTree(member.getMembershipData()).path("history");
		assertEquals("SUSPENDED", history.get(1).path("type").asText());
		assertEquals("RESUMED", history.get(2).path("type").asText());
		assertEquals("actor-uuid", history.get(2).path("actor").asText());
		assertTrue(new JsonSchemaValidator(mapper).validate(mapper.readTree(member.getMembershipData()),
		    SchemaType.MEMBERSHIP_DATA).isValid());
	}

	@Test
	public void shouldCompleteEpisodeAndRejectFurtherTransition() {
		CareModelMember member = service.enrollManually(8, 3, "Initial decision", NOW);
		Date completedAt = new Date(NOW.getTime() + 1000);
		service.transitionEnrollment(member.getUuid(), MemberStatus.COMPLETED, "Goals met", completedAt);
		assertEquals(MemberStatus.COMPLETED, member.getStatus());
		assertEquals(completedAt, member.getDateCompleted());
		try {
			service.transitionEnrollment(member.getUuid(), MemberStatus.ACTIVE, "Reopen", completedAt);
		}
		catch (APIException expected) {
			assertTrue(expected.getMessage().contains("not allowed"));
			return;
		}
		throw new AssertionError("Expected completed membership to be terminal");
	}

	@Test(expected = APIException.class)
	public void shouldRejectLifecycleTransitionForPendingProposal() {
		CareModelMember proposal = service.applyRuleEnrollment(7, 3, "confirm-rule", Action.PROPOSE_CONFIRMATION, "event-8",
		    NOW);
		service.transitionEnrollment(proposal.getUuid(), MemberStatus.SUSPENDED, "Not yet enrolled", NOW);
	}

	@Test(expected = APIException.class)
	public void shouldRejectRuleThatIsNotInPublishedDefinition() {
		service.applyRuleEnrollment(7, 3, "missing", Action.AUTO_ENROLL, "event-6", NOW);
	}

	@Test(expected = APIException.class)
	public void shouldRejectManualEnrollmentWhenDisabled() throws Exception {
		modelDAO.model = model(false);
		service.enrollManually(7, 3, "Not permitted", NOW);
	}

	@Test(expected = APIException.class)
	public void shouldRejectConfirmationWithoutAuditedReason() {
		CareModelMember proposal = service.applyRuleEnrollment(7, 3, "confirm-rule", Action.PROPOSE_CONFIRMATION, "event-7",
		    NOW);
		service.confirmEnrollment(proposal.getUuid(), " ", NOW);
	}

	@Test
	public void shouldRequireTransitionPrivilegeForEveryClinicianInitiatedFlow() throws Exception {
		assertAuthorized("confirmEnrollment", String.class, String.class, Date.class);
		assertAuthorized("declineEnrollment", String.class, String.class, Date.class);
		assertAuthorized("enrollManually", Integer.class, Integer.class, String.class, Date.class);
		assertAuthorized("enrollManually", String.class, String.class, String.class, Date.class);
		assertAuthorized("transitionEnrollment", String.class, MemberStatus.class, String.class, Date.class);
		Authorized lowLevelSave = CareModelMemberService.class.getMethod("saveMember", CareModelMember.class).getAnnotation(
		    Authorized.class);
		assertNotNull(lowLevelSave);
		assertEquals(CareModelsPrivileges.TRANSITION_CARE_MODEL_MEMBERS, lowLevelSave.value()[0]);
	}

	private void assertAuthorized(String method, Class<?>... parameterTypes) throws Exception {
		Authorized authorized = CareModelEnrollmentService.class.getMethod(method, parameterTypes).getAnnotation(
		    Authorized.class);
		assertNotNull(authorized);
		assertEquals(CareModelsPrivileges.TRANSITION_CARE_MODEL_MEMBERS, authorized.value()[0]);
	}

	private CareModel model(boolean manualEnabled) throws Exception {
		JsonNode definition = mapper.readTree("{\"enrollment\":{\"enabled\":true,\"manualEnrollment\":{" + "\"enabled\":"
		        + manualEnabled + ",\"requiresReason\":true},\"rules\":["
		        + "{\"id\":\"auto-rule\",\"action\":\"AUTO_ENROLL\",\"expression\":{\"literal\":\"TRUE\"}},"
		        + "{\"id\":\"confirm-rule\",\"action\":\"PROPOSE_CONFIRMATION\","
		        + "\"expression\":{\"literal\":\"TRUE\"}}]}}");
		StorageEnvelope.PublishedSnapshot published = new StorageEnvelope.PublishedSnapshot();
		published.setVersion("2.1.0");
		published.setDefinition(definition);
		StorageEnvelope envelope = new StorageEnvelope();
		envelope.setPublished(published);
		CareModel model = new CareModel();
		model.setId(3);
		model.setUuid("model-uuid");
		model.setCode("test-model");
		model.setName("Test model");
		model.setPrivilege("View Care Models");
		model.setStatus(CareModelStatus.PUBLISHED);
		model.setRetired(false);
		model.setConfiguration(envelope.toJson(mapper));
		return model;
	}

	private static final class FakeCareModelDAO implements CareModelDAO {

		private CareModel model;

		private int lockCount;

		private FakeCareModelDAO(CareModel model) {
			this.model = model;
		}

		@Override
		public CareModel getCareModelByIdForUpdate(Integer id) {
			lockCount++;
			return model != null && id.equals(model.getId()) ? model : null;
		}

		@Override
		public CareModel getCareModelById(Integer id) {
			return model;
		}

		@Override
		public CareModel saveCareModel(CareModel careModel) {
			return careModel;
		}

		@Override
		public CareModel getCareModelByUuid(String uuid) {
			return model;
		}

		@Override
		public CareModel getCareModelByCode(String code) {
			return model;
		}

		@Override
		public CareModel getCareModelByCodeForUpdate(String code) {
			return model;
		}

		@Override
		public List<CareModel> getCareModels(String qStr, CareModelStatus status, boolean includeRetired,
		        Integer startIndex, Integer limit) {
			return new ArrayList<CareModel>();
		}

		@Override
		public List<CareModel> getPublishedCareModelsForEnrollment() {
			return model == null ? java.util.Collections.<CareModel> emptyList() : java.util.Collections
			        .singletonList(model);
		}

		@Override
		public long getCareModelsCount(String qStr, CareModelStatus status, boolean includeRetired) {
			return 0;
		}

		@Override
		public void purgeCareModel(CareModel careModel) {
		}

		@Override
		public CareModelRef getModelRef(Integer careModelId) {
			return null;
		}

		@Override
		public List<CareModelRef> getModelRefs(Collection<Integer> careModelIds) {
			return new ArrayList<CareModelRef>();
		}
	}

	private static final class FakeMemberDAO implements CareModelMemberDAO {

		private final List<CareModelMember> members = new ArrayList<CareModelMember>();

		@Override
		public CareModelMember saveCareModelMember(CareModelMember member) {
			if (member.getId() == null) {
				member.setId(members.size() + 1);
				members.add(member);
			}
			return member;
		}

		@Override
		public CareModelMember getCareModelMemberById(Integer id) {
			for (CareModelMember member : members) {
				if (id.equals(member.getId())) {
					return member;
				}
			}
			return null;
		}

		@Override
		public CareModelMember getCareModelMemberByUuid(String uuid) {
			for (CareModelMember member : members) {
				if (uuid.equals(member.getUuid())) {
					return member;
				}
			}
			return null;
		}

		@Override
		public CareModelMember getCareModelMemberByUuidForUpdate(String uuid) {
			return getCareModelMemberByUuid(uuid);
		}

		@Override
		public CareModelMember getMemberBySource(Integer patientId, Integer careModelId, String sourceUuid) {
			for (CareModelMember member : members) {
				if (patientId.equals(member.getPatientId()) && careModelId.equals(member.getCareModelId())
				        && sourceUuid.equals(member.getSourceUuid())) {
					return member;
				}
			}
			return null;
		}

		@Override
		public List<CareModelMember> getMembersBySourceForUpdate(Integer patientId, String sourceUuid) {
			List<CareModelMember> result = new ArrayList<CareModelMember>();
			for (CareModelMember member : members) {
				if (patientId.equals(member.getPatientId()) && sourceUuid.equals(member.getSourceUuid())
				        && !Boolean.TRUE.equals(member.getVoided())) {
					result.add(member);
				}
			}
			return result;
		}

		@Override
		public CareModelMember getOpenMember(Integer patientId, Integer careModelId) {
			for (CareModelMember member : members) {
				if (patientId.equals(member.getPatientId())
				        && careModelId.equals(member.getCareModelId())
				        && (member.getStatus() == MemberStatus.PENDING || member.getStatus() == MemberStatus.ACTIVE || member
				                .getStatus() == MemberStatus.SUSPENDED)) {
					return member;
				}
			}
			return null;
		}

		@Override
		public int getNextEpisodeNumber(Integer patientId, Integer careModelId) {
			int maximum = 0;
			for (CareModelMember member : members) {
				if (patientId.equals(member.getPatientId()) && careModelId.equals(member.getCareModelId())) {
					maximum = Math.max(maximum, member.getEpisodeNumber());
				}
			}
			return maximum + 1;
		}

		@Override
		public CareModelMember getCurrentMember(Integer patientId, Integer careModelId) {
			CareModelMember member = getOpenMember(patientId, careModelId);
			return member != null && member.getStatus() != MemberStatus.PENDING ? member : null;
		}

		@Override
		public List<CareModelMember> getMembersByPatientAndModel(Integer patientId, Integer careModelId) {
			return members;
		}

		@Override
		public List<CareModelMember> getActiveMembersByPatient(Integer patientId) {
			return members;
		}

		@Override
		public List<CareModelMember> getActiveMembersByModel(Integer careModelId) {
			return members;
		}

		@Override
		public List<CareModelMember> getMembersByPatientAndStatus(Integer patientId, MemberStatus status,
		        boolean includeVoided, Integer startIndex, Integer limit) {
			return members;
		}

		@Override
		public long getMembersCount(Integer patientId, MemberStatus status, boolean includeVoided) {
			return members.size();
		}

		@Override
		public void voidCareModelMember(CareModelMember member, String reason) {
			member.setVoided(true);
		}

		@Override
		public void unvoidCareModelMember(CareModelMember member) {
			member.setVoided(false);
		}

		@Override
		public void purgeCareModelMember(CareModelMember member) {
			members.remove(member);
		}
	}
}
