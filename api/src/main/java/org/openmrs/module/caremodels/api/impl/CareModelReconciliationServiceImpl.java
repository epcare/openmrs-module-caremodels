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
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.openmrs.GlobalProperty;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.caremodels.api.CareModelEnrollmentService;
import org.openmrs.module.caremodels.api.CareModelReconciliationService;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.api.db.CareModelMemberDAO;
import org.openmrs.module.caremodels.api.db.EnrollmentReconciliationDAO;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.dto.EnrollmentReconciliationResult;
import org.openmrs.module.caremodels.event.EnrollmentRuleEvidenceFactory;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.model.CareModelMember.MemberStatus;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Action;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Decision;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.RuleEvaluation;
import org.openmrs.module.caremodels.rule.RuleEvidence;
import org.openmrs.module.caremodels.rule.RuleEvidenceProvider;
import org.springframework.transaction.annotation.Transactional;

public class CareModelReconciliationServiceImpl extends BaseOpenmrsService implements CareModelReconciliationService {

	static final int DEFAULT_LIMIT = 25;

	static final int MAX_LIMIT = 100;

	private EnrollmentReconciliationDAO reconciliationDAO;

	private CareModelDAO careModelDAO;

	private EnrollmentRuleEvidenceFactory evidenceFactory;

	private EnrollmentRuleEngine ruleEngine = new EnrollmentRuleEngine();

	private CareModelMemberDAO memberDAO;

	public void setMemberDAO(CareModelMemberDAO dao) {
		this.memberDAO = dao;
	}

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

	@Override @Transactional(timeout = 30)
    public int reconcileNextBatch(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Batch size must be 1..100");
        org.openmrs.api.AdministrationService admin = Context.getAdministrationService();
        GlobalProperty checkpoint = careModelDAO.getReconciliationCursorForUpdate();
        if (checkpoint == null) {
            admin.saveGlobalProperty(new GlobalProperty("caremodels.reconciliation.cursor", "{}"));
            checkpoint = careModelDAO.getReconciliationCursorForUpdate();
        }
        if (checkpoint == null) throw new APIException("Reconciliation checkpoint unavailable");
        try {
            JsonNode stored = objectMapper.readTree(checkpoint.getPropertyValue());
            if (stored == null || !stored.isObject()) throw new APIException("Invalid reconciliation checkpoint");
            ObjectNode cursor = (ObjectNode) stored;
            int modelId = cursor.path("modelId").asInt(0);
            CareModel model = modelId == 0 || cursor.path("completed").asBoolean() ? null : careModelDAO.getCareModelById(modelId);
            if (model == null || Boolean.TRUE.equals(model.getRetired()) || model.getStatus() != CareModel.CareModelStatus.PUBLISHED) {
                List<CareModel> models = careModelDAO.getEnrollmentModelsAfter(modelId, 1);
                if (models.isEmpty()) { checkpoint.setPropertyValue("{}"); admin.saveGlobalProperty(checkpoint); return 0; }
                model = models.get(0); cursor = objectMapper.createObjectNode().put("modelId", model.getId());
            }
            StorageEnvelope.PublishedSnapshot published = StorageEnvelope.fromJson(objectMapper, model.getConfiguration()).getPublished();
            if (published == null) throw new APIException("Published model snapshot unavailable");
            if (!published.getChecksum().equals(cursor.path("checksum").asText())) {
                cursor.put("patientId", 0).put("checksum", published.getChecksum()).put("asOf", System.currentTimeMillis())
                    .put("upperPatientId", careModelDAO.getMaximumPatientId()).put("completed", false);
            }
            Date asOf = new Date(cursor.path("asOf").asLong());
            List<Integer> patients = careModelDAO.getPatientIdsAfter(cursor.path("patientId").asInt(), cursor.path("upperPatientId").asInt(), limit);
            for (Integer patientId : patients) {
                reconcileModel(patientId, model, published, asOf);
                cursor.put("patientId", patientId);
            }
            if (patients.size() < limit) cursor.put("completed", true);
            checkpoint.setPropertyValue(cursor.toString()); admin.saveGlobalProperty(checkpoint);
            return patients.size();
        } catch (java.io.IOException | ClassCastException e) { throw new APIException("Invalid reconciliation checkpoint; reset it explicitly", e); }
    }

	/** Caller-managed scan: a failed patient is not included in the continuation cursor. */
	@Override
	@Transactional(timeout = 30)
	public EnrollmentReconciliationResult reconcile(Integer afterPatientId, Integer limit, Date asOf) throws APIException {
		int batchSize = limit == null ? DEFAULT_LIMIT : limit;
		if (batchSize < 1 || batchSize > MAX_LIMIT)
			throw new APIException("Reconciliation limit must be between 1 and " + MAX_LIMIT);
		int cursor = afterPatientId == null ? 0 : afterPatientId;
		if (cursor < 0)
			throw new APIException("Reconciliation cursor must not be negative");
		List<Integer> candidates = reconciliationDAO.getPatientIdsAfter(cursor, batchSize + 1);
		List<CareModel> models = careModelDAO.getPublishedCareModelsForEnrollment();
		// Avoid an unbounded cross-product in an administrator request.
		if (models.size() > MAX_LIMIT)
			throw new APIException("Use scheduled reconciliation when more than 100 models are published");
		Date effectiveAt = asOf == null ? new Date() : new Date(asOf.getTime());
		EnrollmentReconciliationResult result = new EnrollmentReconciliationResult();
		result.setNextAfterPatientId(cursor);
		result.setHasMore(candidates.size() > batchSize);
		for (int i = 0; i < Math.min(candidates.size(), batchSize); i++) {
			Integer patientId = candidates.get(i);
			for (CareModel model : models) {
				result.setEvaluatedModels(result.getEvaluatedModels() + 1);
				// Fail the request rather than continuing with a possibly poisoned Hibernate session.
				// No continuation response is returned; replay starts from the caller's previous cursor.
				StorageEnvelope.PublishedSnapshot published = StorageEnvelope.fromJson(objectMapper,
				    model.getConfiguration()).getPublished();
				if (published == null || published.getDefinition() == null)
					throw new APIException("Published model snapshot unavailable");
				if (reconcileModel(patientId, model, published, effectiveAt))
					result.setAppliedDecisions(result.getAppliedDecisions() + 1);
			}
			result.setNextAfterPatientId(patientId);
			result.setProcessedPatients(result.getProcessedPatients() + 1);
		}
		return result;
	}

	/** Shared by scheduled recovery and explicit administrator backfill. */
	private boolean reconcileModel(Integer patientId, CareModel model,
            StorageEnvelope.PublishedSnapshot published, Date asOf) {
        if (Boolean.TRUE.equals(model.getRetired()) || model.getStatus() != CareModel.CareModelStatus.PUBLISHED)
            return false;
        JsonNode definition = published.getDefinition();
        if (!definition.path("enrollment").path("enabled").asBoolean(false)) return false;
        RuleEvidenceProvider provider = evidenceFactory.create(patientId, definition, asOf);
        final boolean[] unavailable = { false };
        RuleEvidenceProvider checked = (type, alias) -> {
            try {
                RuleEvidence value = provider.resolve(type, alias);
                if (value == null || value.getAvailability() == RuleEvidence.Availability.UNKNOWN) unavailable[0] = true;
                return value == null ? RuleEvidence.unknown() : value;
            } catch (RuntimeException e) {
                unavailable[0] = true;
                return RuleEvidence.unknown();
            }
        };
        Decision decision = ruleEngine.evaluate(definition.path("enrollment").path("rules"), checked, asOf);
        if (unavailable[0]) throw new APIException("Enrollment evidence unavailable; recovery batch will retry");
        CareModelMember current = memberDAO.getOpenMember(patientId, model.getId());
        if (decision.hasMatch() && decision.getSelected().getAction() != Action.EXCLUDE) {
            RuleEvaluation selected = decision.getSelected();
            String source = sourceUuid(patientId, model, published, selected.getRuleId(), selected.getAction(), asOf);
            // Capture state before the enrollment service can promote the same managed object.
            boolean canChange = current == null || (current.getStatus() == MemberStatus.PENDING
                && selected.getAction() == Action.AUTO_ENROLL);
            if (memberDAO.getMemberBySource(patientId, model.getId(), source) != null) return false;
            CareModelMember applied = enrollmentService.applyBackfillEnrollment(patientId, model.getId(),
                selected.getRuleId(), selected.getAction(), source, asOf);
            return canChange && applied != null;
        }
        if (current != null && !"MANUAL".equals(current.getEnrollmentSource()) && current.getSourceUuid() != null)
            return enrollmentService.flagEnrollmentsForReview(patientId, current.getSourceUuid(),
                "Reconciliation flagged this enrollment for review", asOf) > 0;
        return false;
    }

	private String sourceUuid(Integer patientId, CareModel model, StorageEnvelope.PublishedSnapshot published,
	        String ruleId, Action action, Date asOf) {
		String modelKey = model.getUuid() == null ? model.getCode() : model.getUuid();
		// The latest closed episode before evaluation identifies the eligibility period.
		// Newly committed enrollment must not change the source on a page replay.
		int closedEpisode = 0;
		for (CareModelMember episode : memberDAO.getMembersByPatientAndModel(patientId, model.getId())) {
			if (!Boolean.TRUE.equals(episode.getVoided()) && episode.getDateCompleted() != null
			        && episode.getDateCompleted().before(asOf)
			        && (episode.getDateEnrolled() == null || episode.getDateEnrolled().before(asOf)))
				closedEpisode = Math.max(closedEpisode, episode.getEpisodeNumber());
		}
		String key = "caremodels-backfill:" + modelKey + ":" + published.getVersion() + ":" + published.getChecksum() + ":"
		        + patientId + ":" + ruleId + ":" + action + ":" + closedEpisode;
		return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
	}
}
