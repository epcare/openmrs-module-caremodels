/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.api.impl;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.openmrs.api.APIException;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.caremodels.api.CareModelEnrollmentService;
import org.openmrs.module.caremodels.api.CareModelReconciliationService;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.api.db.EnrollmentReconciliationDAO;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.dto.EnrollmentReconciliationResult;
import org.openmrs.module.caremodels.event.EnrollmentRuleEvidenceFactory;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Action;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Decision;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.RuleEvaluation;
import org.openmrs.module.caremodels.rule.RuleEvidenceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class CareModelReconciliationServiceImpl extends BaseOpenmrsService implements CareModelReconciliationService {

	private static final Logger log = LoggerFactory.getLogger(CareModelReconciliationServiceImpl.class);

	static final int DEFAULT_LIMIT = 25;

	static final int MAX_LIMIT = 100;

	private EnrollmentReconciliationDAO reconciliationDAO;

	private CareModelDAO careModelDAO;

	private EnrollmentRuleEvidenceFactory evidenceFactory;

	private EnrollmentRuleEngine ruleEngine;

	private CareModelEnrollmentService enrollmentService;

	private ObjectMapper objectMapper = new ObjectMapper();

	public void setReconciliationDAO(EnrollmentReconciliationDAO reconciliationDAO) {
		this.reconciliationDAO = reconciliationDAO;
	}

	public void setCareModelDAO(CareModelDAO careModelDAO) {
		this.careModelDAO = careModelDAO;
	}

	public void setEvidenceFactory(EnrollmentRuleEvidenceFactory evidenceFactory) {
		this.evidenceFactory = evidenceFactory;
	}

	public void setRuleEngine(EnrollmentRuleEngine ruleEngine) {
		this.ruleEngine = ruleEngine;
	}

	public void setEnrollmentService(CareModelEnrollmentService enrollmentService) {
		this.enrollmentService = enrollmentService;
	}

	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	@Transactional
	public EnrollmentReconciliationResult reconcile(Integer afterPatientId, Integer limit, Date asOf) throws APIException {
		int batchSize = limit == null ? DEFAULT_LIMIT : limit;
		if (batchSize < 1 || batchSize > MAX_LIMIT) {
			throw new APIException("Reconciliation limit must be between 1 and " + MAX_LIMIT);
		}
		int cursor = afterPatientId == null ? 0 : afterPatientId;
		if (cursor < 0) {
			throw new APIException("Reconciliation cursor must not be negative");
		}

		List<Integer> candidates = reconciliationDAO.getPatientIdsAfter(cursor, batchSize + 1);
		boolean hasMore = candidates.size() > batchSize;
		List<CareModel> models = careModelDAO.getPublishedCareModelsForEnrollment();
		Date effectiveAt = asOf == null ? new Date() : new Date(asOf.getTime());
		EnrollmentReconciliationResult result = new EnrollmentReconciliationResult();
		int processed = Math.min(candidates.size(), batchSize);
		for (int i = 0; i < processed; i++) {
			Integer patientId = candidates.get(i);
			result.setNextAfterPatientId(patientId);
			for (CareModel model : models) {
				result.setEvaluatedModels(result.getEvaluatedModels() + 1);
				try {
					if (reconcileModel(patientId, model, effectiveAt)) {
						result.setAppliedDecisions(result.getAppliedDecisions() + 1);
					}
				}
				catch (RuntimeException e) {
					result.setFailedEvaluations(result.getFailedEvaluations() + 1);
					log.warn("Reconciliation failed for patient {} and care model {}", patientId, model.getCode(), e);
				}
			}
		}
		result.setProcessedPatients(processed);
		result.setHasMore(hasMore);
		if (processed == 0) {
			result.setNextAfterPatientId(cursor);
		}
		return result;
	}

	private boolean reconcileModel(Integer patientId, CareModel model, Date asOf) {
		StorageEnvelope envelope = StorageEnvelope.fromJson(objectMapper, model.getConfiguration());
		if (envelope.getPublished() == null || envelope.getPublished().getDefinition() == null) {
			return false;
		}
		JsonNode definition = envelope.getPublished().getDefinition();
		if (!definition.path("enrollment").path("enabled").asBoolean(false)) {
			return false;
		}
		RuleEvidenceProvider evidence = evidenceFactory.create(patientId, definition, asOf);
		Decision decision = ruleEngine.evaluate(definition.path("enrollment").path("rules"), evidence, asOf);
		if (!decision.hasMatch() || decision.getSelected().getAction() == Action.EXCLUDE) {
			return false;
		}
		RuleEvaluation selected = decision.getSelected();
		String sourceUuid = sourceUuid(patientId, model, envelope.getPublished().getVersion(), selected.getRuleId());
		enrollmentService.applyBackfillEnrollment(patientId, model.getId(), selected.getRuleId(), selected.getAction(),
		    sourceUuid, asOf);
		return true;
	}

	private String sourceUuid(Integer patientId, CareModel model, String version, String ruleId) {
		String modelKey = model.getUuid() == null ? model.getCode() : model.getUuid();
		String key = "caremodels-backfill:" + modelKey + ":" + version + ":" + patientId + ":" + ruleId;
		return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
	}
}
