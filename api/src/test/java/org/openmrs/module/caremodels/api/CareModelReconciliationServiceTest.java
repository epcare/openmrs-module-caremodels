/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIException;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.api.db.EnrollmentReconciliationDAO;
import org.openmrs.module.caremodels.api.impl.CareModelReconciliationServiceImpl;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.dto.CareModelRef;
import org.openmrs.module.caremodels.dto.EnrollmentReconciliationResult;
import org.openmrs.module.caremodels.event.EnrollmentRuleEvidenceFactory;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.model.CareModelMember.MemberStatus;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Action;
import org.openmrs.module.caremodels.rule.RuleEvidence;
import org.openmrs.module.caremodels.rule.RuleEvidenceProvider;
import org.openmrs.module.caremodels.security.CareModelsPrivileges;

public class CareModelReconciliationServiceTest {
	
	private static final Date NOW = new Date(1761033600000L);
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private final FakeReconciliationDAO reconciliationDAO = new FakeReconciliationDAO();
	
	private final FakeCareModelDAO modelDAO = new FakeCareModelDAO();
	
	private final RecordingEnrollmentService enrollmentService = new RecordingEnrollmentService();
	
	private CareModelReconciliationServiceImpl service;
	
	@Before
	public void setup() {
		service = new CareModelReconciliationServiceImpl();
		service.setReconciliationDAO(reconciliationDAO);
		service.setMemberDAO(org.mockito.Mockito.mock(org.openmrs.module.caremodels.api.db.CareModelMemberDAO.class));
		service.setCareModelDAO(modelDAO);
		service.setRuleEngine(new EnrollmentRuleEngine());
		service.setEnrollmentService(enrollmentService);
		service.setEvidenceFactory(new EnrollmentRuleEvidenceFactory() {
			
			@Override
			public Integer resolvePatientId(String patientUuid) {
				return null;
			}
			
			@Override
			public RuleEvidenceProvider create(Integer patientId, JsonNode definition, Date asOf) {
				return new RuleEvidenceProvider() {
					
					@Override
					public RuleEvidence resolve(String type, String alias) {
						return RuleEvidence.present(true);
					}
				};
			}
		});
	}
	
	@Test
	public void shouldReturnResumableCursorAndDeterministicBackfillSources() throws Exception {
		reconciliationDAO.patientIds.add(2);
		reconciliationDAO.patientIds.add(4);
		reconciliationDAO.patientIds.add(6);
		modelDAO.models.add(model(3, "model-a", "1.2.0"));
		
		EnrollmentReconciliationResult first = service.reconcile(0, 2, NOW);
		assertEquals(2, first.getProcessedPatients());
		assertEquals(Integer.valueOf(4), first.getNextAfterPatientId());
		assertTrue(first.isHasMore());
		assertEquals(2, first.getEvaluatedModels());
		assertEquals(2, first.getAppliedDecisions());
		assertEquals(2, enrollmentService.calls.size());
		
		String firstSource = enrollmentService.calls.get(0).sourceUuid;
		service.reconcile(0, 2, NOW);
		assertEquals(firstSource, enrollmentService.calls.get(2).sourceUuid);
		assertEquals(Action.AUTO_ENROLL, enrollmentService.calls.get(0).action);
	}
	
	@Test(expected = IllegalStateException.class)
	public void shouldFailWithoutReturningAnAdvancedCursorForMalformedModel() throws Exception {
		reconciliationDAO.patientIds.add(9);
		CareModel malformed = new CareModel();
		malformed.setId(2);
		malformed.setCode("malformed");
		malformed.setConfiguration("{");
		modelDAO.models.add(malformed);
		service.reconcile(null, null, NOW);
	}
	
	@Test(expected = APIException.class)
	public void shouldRejectUnboundedBatch() {
		service.reconcile(0, 101, NOW);
	}
	
	@Test
	public void shouldRequireManagePrivilege() throws Exception {
		Authorized authorized = CareModelReconciliationService.class.getMethod("reconcile", Integer.class, Integer.class,
		    Date.class).getAnnotation(Authorized.class);
		assertNotNull(authorized);
		assertEquals(CareModelsPrivileges.MANAGE_CARE_MODELS, authorized.value()[0]);
	}
	
	private CareModel model(int id, String code, String version) throws Exception {
		JsonNode definition = mapper.readTree("{\"enrollment\":{\"enabled\":true,\"rules\":["
		        + "{\"id\":\"backfill-rule\",\"action\":\"AUTO_ENROLL\"," + "\"expression\":{\"literal\":\"TRUE\"}}]}}");
		StorageEnvelope.PublishedSnapshot published = new StorageEnvelope.PublishedSnapshot();
		published.setVersion(version);
		published.setDefinition(definition);
		StorageEnvelope envelope = new StorageEnvelope();
		envelope.setPublished(published);
		CareModel model = new CareModel();
		model.setId(id);
		model.setUuid("00000000-0000-0000-0000-00000000000" + id);
		model.setCode(code);
		model.setStatus(CareModelStatus.PUBLISHED);
		model.setRetired(false);
		model.setConfiguration(envelope.toJson(mapper));
		return model;
	}
	
	private static final class FakeReconciliationDAO implements EnrollmentReconciliationDAO {
		
		private final List<Integer> patientIds = new ArrayList<Integer>();
		
		@Override
		public List<Integer> getPatientIdsAfter(Integer afterPatientId, int limit) {
			List<Integer> result = new ArrayList<Integer>();
			int cursor = afterPatientId == null ? 0 : afterPatientId;
			for (Integer patientId : patientIds) {
				if (patientId > cursor && result.size() < limit) {
					result.add(patientId);
				}
			}
			return result;
		}
	}
	
	private static final class BackfillCall {
		
		private String sourceUuid;
		
		private Action action;
	}
	
	private static final class RecordingEnrollmentService implements CareModelEnrollmentService {
		
		private final List<BackfillCall> calls = new ArrayList<BackfillCall>();
		
		@Override
		public CareModelMember applyBackfillEnrollment(Integer patientId, Integer careModelId, String ruleId, Action action,
		        String sourceUuid, Date asOf) {
			BackfillCall call = new BackfillCall();
			call.sourceUuid = sourceUuid;
			call.action = action;
			calls.add(call);
			return new CareModelMember();
		}
		
		@Override
		public CareModelMember applyRuleEnrollment(Integer patientId, Integer careModelId, String ruleId, Action action,
		        String sourceUuid, Date asOf) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public int flagEnrollmentsForReview(Integer patientId, String sourceUuid, String reason, Date asOf) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public CareModelMember confirmEnrollment(String memberUuid, String reason, Date asOf) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public CareModelMember declineEnrollment(String memberUuid, String reason, Date asOf) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public CareModelMember enrollManually(Integer patientId, Integer careModelId, String reason, Date asOf) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public CareModelMember enrollManually(String patientUuid, String modelCodeOrUuid, String reason, Date asOf) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public CareModelMember transitionEnrollment(String memberUuid, MemberStatus newStatus, String reason, Date asOf) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public void onStartup() {
		}
		
		@Override
		public void onShutdown() {
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
			return new ArrayList<CareModel>();
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
}
