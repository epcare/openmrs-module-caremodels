/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.api.impl;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.openmrs.module.caremodels.config.StorageEnvelopeCache;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

public class CareModelReconciliationServiceImpl extends BaseOpenmrsService implements CareModelReconciliationService {
	
	private static final Logger log = LoggerFactory.getLogger(CareModelReconciliationServiceImpl.class);
	
	static final int DEFAULT_LIMIT = 25;
	
	static final int MAX_LIMIT = 100;
	
	/**
	 * Outer checkpoint transaction only (cursor decisions + bounded GP writes); it holds the
	 * checkpoint row lock for the batch while each patient's evaluation commits in its own
	 * REQUIRES_NEW transaction, so one slow or failing patient cannot void the whole batch.
	 */
	private static final int CHECKPOINT_TIMEOUT_SECONDS = 600;
	
	/** Evidence evaluation per patient; an individual patient can never wedge the batch. */
	private static final int PATIENT_TIMEOUT_SECONDS = 30;
	
	/**
	 * Default look-back for candidate gating ({@code caremodels.reconciliation.activityWindowDays}
	 * ).
	 */
	static final int DEFAULT_ACTIVITY_WINDOW_DAYS = 90;
	
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
	
	private StorageEnvelopeCache envelopeCache = new StorageEnvelopeCache();
	
	private PlatformTransactionManager transactionManager;
	
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
	
	public void setEnvelopeCache(StorageEnvelopeCache envelopeCache) {
		this.envelopeCache = envelopeCache;
	}
	
	public void setTransactionManager(PlatformTransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}
	
	/**
	 * Explicit transaction orchestration instead of one batch-wide transaction: the checkpoint
	 * transaction (row lock + cursor writes) survives the whole batch, while every patient's
	 * evidence evaluation and enrollment writes commit independently with its own timeout. A failed
	 * patient is logged with a full stack trace, excluded from the continuation cursor and retried
	 * on the next run; already-completed patients in this batch keep their commits. Without a
	 * transaction manager (unit tests), the batch runs inline.
	 */
	@Override
	public int reconcileNextBatch(int limit) {
		if (limit < 1 || limit > 100) throw new IllegalArgumentException("Batch size must be 1..100");
		final int[] processed = { 0 };
		if (transactionManager == null) {
			runBatch(limit, processed);
		} else {
			newCheckpointTemplate().execute(status -> {
				runBatch(limit, processed);
				return null;
			});
		}
		return processed[0];
	}
	
	private void runBatch(int limit, int[] processed) {
		org.openmrs.api.AdministrationService admin = Context.getAdministrationService();
		GlobalProperty checkpoint = careModelDAO.getReconciliationCursorForUpdate();
		if (checkpoint == null) {
			admin.saveGlobalProperty(new GlobalProperty("caremodels.reconciliation.cursor", "{}"));
			checkpoint = careModelDAO.getReconciliationCursorForUpdate();
		}
		if (checkpoint == null)
			throw new APIException("Reconciliation checkpoint unavailable");
		JsonNode stored;
		try {
			stored = objectMapper.readTree(checkpoint.getPropertyValue());
		}
		catch (java.io.IOException e) {
			throw new APIException("Invalid reconciliation checkpoint; reset it explicitly", e);
		}
		if (stored == null || !stored.isObject())
			throw new APIException("Invalid reconciliation checkpoint");
		ObjectNode cursor = (ObjectNode) stored;
		int modelId = cursor.path("modelId").asInt(0);
		CareModel model = modelId == 0 || cursor.path("completed").asBoolean() ? null : careModelDAO
		        .getCareModelById(modelId);
		if (model == null || Boolean.TRUE.equals(model.getRetired())
		        || model.getStatus() != CareModel.CareModelStatus.PUBLISHED) {
			List<CareModel> models = careModelDAO.getEnrollmentModelsAfter(modelId, 1);
			if (models.isEmpty()) {
				checkpoint.setPropertyValue("{}");
				admin.saveGlobalProperty(checkpoint);
				return;
			}
			model = models.get(0);
			cursor = objectMapper.createObjectNode().put("modelId", model.getId());
		}
		StorageEnvelope.PublishedSnapshot published = envelopeCache.get(model.getId(), model.getConfiguration())
		        .getPublished();
		if (published == null)
			throw new APIException("Published model snapshot unavailable");
		if (!published.getChecksum().equals(cursor.path("checksum").asText())) {
			cursor.put("patientId", 0).put("checksum", published.getChecksum()).put("asOf", System.currentTimeMillis())
			        .put("upperPatientId", careModelDAO.getMaximumPatientId()).put("completed", false);
		}
		Date asOf = new Date(cursor.path("asOf").asLong());
		int afterPatientId = cursor.path("patientId").asInt();
		int upperPatientId = cursor.path("upperPatientId").asInt();
		List<Integer> patients = selectCandidatePatients(afterPatientId, upperPatientId, asOf, limit);
		final CareModel batchModel = model;
		final StorageEnvelope.PublishedSnapshot batchPublished = published;
		final Date batchAsOf = asOf;
		for (Integer patientId : patients) {
			try {
				evaluatePatientInNewTransaction(patientId, batchModel, batchPublished, batchAsOf);
			}
			catch (RuntimeException e) {
				// Full stack trace: this loop retries the same patient on every run, so the
				// cause must be diagnosable from the log alone.
				log.warn("Reconciliation failed for patient {} in model {}; cursor retained for retry", patientId,
				    batchModel.getCode(), e);
				break;
			}
			cursor.put("patientId", patientId);
			checkpoint.setPropertyValue(cursor.toString());
			admin.saveGlobalProperty(checkpoint);
			processed[0]++;
		}
		if (patients.size() < limit)
			cursor.put("completed", true);
		checkpoint.setPropertyValue(cursor.toString());
		admin.saveGlobalProperty(checkpoint);
	}
	
	/**
	 * One patient's evaluation commits in its own REQUIRES_NEW transaction; inline without a
	 * manager.
	 */
	private void evaluatePatientInNewTransaction(Integer patientId, CareModel model,
	        StorageEnvelope.PublishedSnapshot published, Date asOf) {
		if (transactionManager == null) {
			reconcileModel(patientId, model, published, asOf);
			return;
		}
		newPatientTemplate().execute(tx -> {
			reconcileModel(patientId, model, published, asOf);
			return null;
		});
	}
	
	/**
	 * Candidates for one batch: patients with clinical activity inside the configured look-back
	 * window when {@code caremodels.reconciliation.activityWindowDays} is set; the full population
	 * when the window is 0. A window-bounded cycle intentionally finishes early — patients without
	 * activity older than the window cannot have a recent missed event to recover.
	 */
	private List<Integer> selectCandidatePatients(int afterPatientId, int upperPatientId, Date asOf, int limit) {
		int windowDays = DEFAULT_ACTIVITY_WINDOW_DAYS;
		try {
			String configured = Context.getAdministrationService().getGlobalProperty(
			    "caremodels.reconciliation.activityWindowDays", String.valueOf(DEFAULT_ACTIVITY_WINDOW_DAYS));
			windowDays = Integer.parseInt(configured.trim());
		}
		catch (RuntimeException e) {
			// unconfigured or unreadable property: use the default window
			windowDays = DEFAULT_ACTIVITY_WINDOW_DAYS;
		}
		if (windowDays < 0 || windowDays > 3650) {
			log.warn("Invalid caremodels.reconciliation.activityWindowDays ({}); using default {}", windowDays,
			    DEFAULT_ACTIVITY_WINDOW_DAYS);
			windowDays = DEFAULT_ACTIVITY_WINDOW_DAYS;
		}
		if (windowDays == 0) {
			return careModelDAO.getPatientIdsAfter(afterPatientId, upperPatientId, limit);
		}
		Date activeSince = new Date(asOf.getTime() - windowDays * 86400000L);
		return careModelDAO.getActivePatientIdsAfter(afterPatientId, upperPatientId, activeSince, limit);
	}
	
	private TransactionTemplate newCheckpointTemplate() {
		if (transactionManager == null) {
			throw new APIException("Reconciliation transaction manager is not configured");
		}
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		template.setTimeout(CHECKPOINT_TIMEOUT_SECONDS);
		return template;
	}
	
	private TransactionTemplate newPatientTemplate() {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		template.setTimeout(PATIENT_TIMEOUT_SECONDS);
		return template;
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
		// Parse each model's envelope ONCE for the whole batch, before the patient loop; parsed
		// trees come from the shared cache and are read-only here.
		Map<Integer, StorageEnvelope.PublishedSnapshot> publishedByModelId = new HashMap<Integer, StorageEnvelope.PublishedSnapshot>();
		for (CareModel model : models) {
			StorageEnvelope.PublishedSnapshot published = envelopeCache.get(model.getId(), model.getConfiguration())
			        .getPublished();
			if (published == null || published.getDefinition() == null)
				throw new APIException("Published model snapshot unavailable for model '" + model.getCode() + "'");
			publishedByModelId.put(model.getId(), published);
		}
		Date effectiveAt = asOf == null ? new Date() : new Date(asOf.getTime());
		EnrollmentReconciliationResult result = new EnrollmentReconciliationResult();
		result.setNextAfterPatientId(cursor);
		result.setHasMore(candidates.size() > batchSize);
		for (int i = 0; i < Math.min(candidates.size(), batchSize); i++) {
			Integer patientId = candidates.get(i);
			for (CareModel model : models) {
				result.setEvaluatedModels(result.getEvaluatedModels() + 1);
				if (reconcileModel(patientId, model, publishedByModelId.get(model.getId()), effectiveAt))
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
