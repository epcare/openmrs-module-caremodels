/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.APIException;
import org.openmrs.module.caremodels.api.CareModelEnrollmentService;
import org.openmrs.module.caremodels.dto.ManualEnrollmentOption;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.dto.CareModelRef;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Action;
import org.openmrs.module.caremodels.rule.RuleEvidence;
import org.openmrs.module.caremodels.rule.RuleEvidenceProvider;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClinicalChangeEnrollmentProcessorTest {
	
	private static final Date NOW = new Date(1720000000000L);
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private final FakeCareModelDAO dao = new FakeCareModelDAO();
	
	private final RecordingEnrollmentService enrollment = new RecordingEnrollmentService();
	
	private ClinicalChangeEnrollmentProcessor processor;
	
	@Before
	public void setUp() {
		processor = new ClinicalChangeEnrollmentProcessor();
		processor.setCareModelDAO(dao);
		processor.setTriggerMatcher(new EnrollmentTriggerMatcher());
		processor.setRuleEngine(new EnrollmentRuleEngine());
		processor.setEnrollmentService(enrollment);
		processor.setEvidenceFactory(new EnrollmentRuleEvidenceFactory() {
			
			@Override
			public Integer resolvePatientId(String patientUuid) {
				return "patient-uuid".equals(patientUuid) ? 17 : null;
			}
			
			@Override
			public RuleEvidenceProvider create(Integer patientId, JsonNode definition, Date asOf) {
				return new RuleEvidenceProvider() {
					
					@Override
					public RuleEvidence resolve(String type, String alias) {
						return "positive".equals(alias) ? RuleEvidence.present(true) : RuleEvidence.unknown();
					}
				};
			}
		});
	}
	
	@Test
	public void shouldEvaluateMatchingEventAndPersistSelectedDecision() throws Exception {
		dao.models
		        .add(model(3, "auto", rules(rule("exclude", "EXCLUDE", "FALSE"), rule("auto-rule", "AUTO_ENROLL", "TRUE"))));
		
		processor.onClinicalChange(event("source-1", "encounter-type"));
		
		assertEquals(1, enrollment.calls.size());
		Call call = enrollment.calls.get(0);
		assertEquals(Integer.valueOf(17), call.patientId);
		assertEquals(Integer.valueOf(3), call.careModelId);
		assertEquals("auto-rule", call.ruleId);
		assertEquals(Action.AUTO_ENROLL, call.action);
		assertEquals("source-1", call.sourceUuid);
		assertEquals(NOW, call.asOf);
	}
	
	@Test
	public void exclusionShouldWinWithoutWritingMembership() throws Exception {
		dao.models.add(model(3, "excluded", rules(rule("auto", "AUTO_ENROLL", "TRUE"), rule("exclude", "EXCLUDE", "TRUE"))));
		
		processor.onClinicalChange(event("source-2", "encounter-type"));
		
		assertTrue(enrollment.calls.isEmpty());
	}
	
	@Test
	public void shouldIgnoreUnmatchedTriggerUnknownEvidenceAndMissingPatient() throws Exception {
		dao.models.add(model(3, "conditional", "[{\"id\":\"conditional\",\"action\":\"AUTO_ENROLL\","
		        + "\"expression\":{\"type\":\"observation\",\"alias\":\"missing\",\"operator\":\"EXISTS\"}}]"));
		
		processor.onClinicalChange(event("source-3", "different-type"));
		processor.onClinicalChange(event("source-4", "encounter-type"));
		processor.onClinicalChange(new ClinicalChangeEvent(ClinicalChangeEvent.TriggerType.ENCOUNTER_SAVED, "missing",
		        "source-5", "encounter-type", NOW));
		
		assertTrue(enrollment.calls.isEmpty());
	}
	
	@Test
	public void malformedModelShouldNotPreventEvaluationOfNextModel() throws Exception {
		CareModel malformed = new CareModel();
		malformed.setId(2);
		malformed.setCode("malformed");
		malformed.setConfiguration("{");
		dao.models.add(malformed);
		dao.models.add(model(3, "healthy", rules(rule("confirm", "PROPOSE_CONFIRMATION", "TRUE"))));
		
		processor.onClinicalChange(event("source-6", "encounter-type"));
		
		assertEquals(1, enrollment.calls.size());
		assertEquals(Action.PROPOSE_CONFIRMATION, enrollment.calls.get(0).action);
	}
	
	@Test
	public void correctedEvidenceShouldBeFlaggedEvenWhenNoEnrollmentTriggerMatches() throws Exception {
		dao.models.add(model(3, "unmatched", rules(rule("auto", "AUTO_ENROLL", "TRUE"))));
		ClinicalChangeEvent event = new ClinicalChangeEvent(ClinicalChangeEvent.TriggerType.OBSERVATION_CHANGED,
		        "patient-uuid", "replacement-obs", "different-concept", NOW, "original-obs");
		
		processor.onClinicalChange(event);
		
		assertEquals(1, enrollment.reviewCalls.size());
		ReviewCall call = enrollment.reviewCalls.get(0);
		assertEquals(Integer.valueOf(17), call.patientId);
		assertEquals("original-obs", call.sourceUuid);
		assertTrue(call.reason.contains("OBSERVATION_CHANGED"));
		assertEquals(NOW, call.asOf);
		assertTrue(enrollment.calls.isEmpty());
	}
	
	@Test
	public void listenerShouldAlwaysRunInNewTransaction() throws Exception {
		Method method = ClinicalChangeEnrollmentProcessor.class.getMethod("onClinicalChange", ClinicalChangeEvent.class);
		Transactional annotation = method.getAnnotation(Transactional.class);
		
		assertEquals(Propagation.REQUIRES_NEW, annotation.propagation());
		Method persistence = CareModelEnrollmentService.class.getMethod("applyRuleEnrollment", Integer.class, Integer.class,
		    String.class, Action.class, String.class, Date.class);
		assertEquals(Propagation.REQUIRES_NEW, persistence.getAnnotation(Transactional.class).propagation());
		Method review = CareModelEnrollmentService.class.getMethod("flagEnrollmentsForReview", Integer.class, String.class,
		    String.class, Date.class);
		assertEquals(Propagation.REQUIRES_NEW, review.getAnnotation(Transactional.class).propagation());
	}
	
	@Test
	public void correctionShouldReviewMembershipWithoutCreatingAnotherEpisode() throws Exception {
		dao.models.add(model(3, "review", rules(rule("auto", "AUTO_ENROLL", "TRUE"))));
		processor.onClinicalChange(new ClinicalChangeEvent(ClinicalChangeEvent.TriggerType.ENCOUNTER_VOIDED, "patient-uuid",
		        "corrected-source", "encounter-type", NOW, "corrected-source"));
		assertEquals(1, enrollment.reviewCalls.size());
		assertEquals("corrected-source", enrollment.reviewCalls.get(0).sourceUuid);
		assertTrue(enrollment.calls.isEmpty());
	}
	
	private CareModel model(int id, String code, String rules) throws Exception {
		JsonNode definition = mapper.readTree("{\"schemaVersion\":\"1.0.0\",\"terminology\":{"
		        + "\"encounterTypes\":{\"visit\":\"encounter-type\"}},\"enrollment\":{\"enabled\":true,"
		        + "\"triggers\":[{\"type\":\"ENCOUNTER_SAVED\",\"encounterTypeAlias\":\"visit\"}]," + "\"rules\":" + rules
		        + "}}");
		StorageEnvelope envelope = new StorageEnvelope();
		envelope.saveDraft(definition, 0).publishDraft("1.0.0", 1, NOW);
		CareModel model = new CareModel();
		model.setId(id);
		model.setCode(code);
		model.setStatus(CareModelStatus.PUBLISHED);
		model.setRetired(false);
		model.setConfiguration(envelope.toJson(mapper));
		return model;
	}
	
	private String rules(String... rules) {
		StringBuilder result = new StringBuilder("[");
		for (int i = 0; i < rules.length; i++) {
			if (i > 0) {
				result.append(',');
			}
			result.append(rules[i]);
		}
		return result.append(']').toString();
	}
	
	private String rule(String id, String action, String literal) {
		return "{\"id\":\"" + id + "\",\"action\":\"" + action + "\",\"expression\":{\"literal\":\"" + literal + "\"}}";
	}
	
	private ClinicalChangeEvent event(String sourceUuid, String referenceUuid) {
		return new ClinicalChangeEvent(ClinicalChangeEvent.TriggerType.ENCOUNTER_SAVED, "patient-uuid", sourceUuid,
		        referenceUuid, NOW);
	}
	
	private static final class Call {
		
		private Integer patientId;
		
		private Integer careModelId;
		
		private String ruleId;
		
		private Action action;
		
		private String sourceUuid;
		
		private Date asOf;
	}
	
	private static final class ReviewCall {
		
		private Integer patientId;
		
		private String sourceUuid;
		
		private String reason;
		
		private Date asOf;
	}
	
	private static final class RecordingEnrollmentService implements CareModelEnrollmentService {
		
		private final List<Call> calls = new ArrayList<Call>();
		
		private final List<ReviewCall> reviewCalls = new ArrayList<ReviewCall>();
		
		@Override
		public void onStartup() {
		}
		
		@Override
		public void onShutdown() {
		}
		
		@Override
		public CareModelMember applyRuleEnrollment(Integer patientId, Integer careModelId, String ruleId, Action action,
		        String sourceUuid, Date asOf) throws APIException {
			Call call = new Call();
			call.patientId = patientId;
			call.careModelId = careModelId;
			call.ruleId = ruleId;
			call.action = action;
			call.sourceUuid = sourceUuid;
			call.asOf = asOf;
			calls.add(call);
			return null;
		}
		
		@Override
		public CareModelMember applyBackfillEnrollment(Integer patientId, Integer careModelId, String ruleId, Action action,
		        String sourceUuid, Date asOf) throws APIException {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public int flagEnrollmentsForReview(Integer patientId, String sourceUuid, String reason, Date asOf)
		        throws APIException {
			ReviewCall call = new ReviewCall();
			call.patientId = patientId;
			call.sourceUuid = sourceUuid;
			call.reason = reason;
			call.asOf = asOf;
			reviewCalls.add(call);
			return 1;
		}
		
		@Override
		public CareModelMember confirmEnrollment(String memberUuid, String reason, Date asOf) throws APIException {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public CareModelMember declineEnrollment(String memberUuid, String reason, Date asOf) throws APIException {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public CareModelMember enrollManually(Integer patientId, Integer careModelId, String reason, Date asOf)
		        throws APIException {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public CareModelMember enrollManually(String patientUuid, String modelCodeOrUuid, String reason, Date asOf)
		        throws APIException {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public CareModelMember transitionEnrollment(String memberUuid, CareModelMember.MemberStatus newStatus,
		        String reason, Date asOf) throws APIException {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public List<ManualEnrollmentOption> listManualEnrollmentOptions(String patientUuid) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public ManualEnrollmentOption evaluateManualEnrollment(String patientUuid, String modelCodeOrUuid) {
			throw new UnsupportedOperationException();
		}
	}
	
	private static final class FakeCareModelDAO implements CareModelDAO {
		
		private final List<CareModel> models = new ArrayList<CareModel>();
		
		@Override
		public List<CareModel> getPublishedCareModelsForEnrollment() {
			return models;
		}
		
		@Override
		public CareModel saveCareModel(CareModel careModel) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public CareModel getCareModelById(Integer id) {
			return null;
		}
		
		@Override
		public CareModel getCareModelByIdForUpdate(Integer id) {
			return null;
		}
		
		@Override
		public CareModel getCareModelByUuid(String uuid) {
			return null;
		}
		
		@Override
		public CareModel getCareModelByCode(String code) {
			return null;
		}
		
		@Override
		public CareModel getCareModelByCodeForUpdate(String code) {
			return null;
		}
		
		@Override
		public List<CareModel> getCareModels(String qStr, CareModelStatus status, boolean includeRetired,
		        Integer startIndex, Integer limit) {
			return Collections.emptyList();
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
			return Collections.emptyList();
		}
	}
}
