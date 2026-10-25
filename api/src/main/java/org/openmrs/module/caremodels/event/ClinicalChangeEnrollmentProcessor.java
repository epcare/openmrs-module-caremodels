/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.openmrs.module.caremodels.api.CareModelEnrollmentService;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.config.StorageEnvelopeCache;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Action;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Decision;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.RuleEvaluation;
import org.openmrs.module.caremodels.rule.RuleEvidenceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Evaluates a committed clinical change against every matching published Care Model. */
public class ClinicalChangeEnrollmentProcessor implements ClinicalChangeListener {
	
	private static final Logger log = LoggerFactory.getLogger(ClinicalChangeEnrollmentProcessor.class);
	
	private CareModelDAO careModelDAO;
	
	private EnrollmentTriggerMatcher triggerMatcher;
	
	private EnrollmentRuleEngine ruleEngine;
	
	private EnrollmentRuleEvidenceFactory evidenceFactory;
	
	private CareModelEnrollmentService enrollmentService;
	
	private ObjectMapper objectMapper = new ObjectMapper();
	
	private StorageEnvelopeCache envelopeCache = new StorageEnvelopeCache();
	
	public void setCareModelDAO(CareModelDAO careModelDAO) {
		this.careModelDAO = careModelDAO;
	}
	
	public void setTriggerMatcher(EnrollmentTriggerMatcher triggerMatcher) {
		this.triggerMatcher = triggerMatcher;
	}
	
	public void setRuleEngine(EnrollmentRuleEngine ruleEngine) {
		this.ruleEngine = ruleEngine;
	}
	
	public void setEvidenceFactory(EnrollmentRuleEvidenceFactory evidenceFactory) {
		this.evidenceFactory = evidenceFactory;
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
	
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onClinicalChange(ClinicalChangeEvent event) {
		if (event == null) {
			return;
		}
		Integer patientId = evidenceFactory.resolvePatientId(event.getPatientUuid());
		if (patientId == null) {
			log.warn("Ignoring clinical change: patient unavailable");
			return;
		}
		if (event.getReviewSourceUuid() != null) {
			try {
				enrollmentService.flagEnrollmentsForReview(patientId, event.getReviewSourceUuid(),
				    reviewReason(event.getTriggerType()), event.getOccurredAt());
			}
			catch (RuntimeException e) {
				// Class name only: exception messages on this path can carry clinical provenance,
				// and the clinical-change path must not write patient-identifying log detail.
				log.error("Failed to flag corrected or voided enrollment source ({})", e.getClass().getSimpleName());
			}
		}
		
		List<CareModel> models = careModelDAO.getPublishedCareModelsForEnrollment();
		for (CareModel model : models) {
			try {
				evaluateModel(patientId, model, event);
			}
			catch (RuntimeException e) {
				log.error("Enrollment evaluation failed ({}) in model {}; reconciliation required", e.getClass()
				        .getSimpleName(), model.getCode());
			}
		}
	}
	
	private String reviewReason(ClinicalChangeEvent.TriggerType triggerType) {
		return "Enrollment source evidence was corrected or voided (" + triggerType.name() + ")";
	}
	
	private void evaluateModel(Integer patientId, CareModel model, ClinicalChangeEvent event) {
		// parsed envelope served from the shared cache; the definition tree is read-only here
		StorageEnvelope envelope = envelopeCache.get(model.getId(), model.getConfiguration());
		if (envelope.getPublished() == null || envelope.getPublished().getDefinition() == null) {
			return;
		}
		JsonNode definition = envelope.getPublished().getDefinition();
		if (!triggerMatcher.matches(definition, event)) {
			return;
		}
		
		RuleEvidenceProvider evidence = evidenceFactory.create(patientId, definition, event.getOccurredAt());
		Decision decision = ruleEngine
		        .evaluate(definition.path("enrollment").path("rules"), evidence, event.getOccurredAt());
		if (!decision.hasMatch()) {
			return;
		}
		RuleEvaluation selected = decision.getSelected();
		if (selected.getAction() == Action.EXCLUDE) {
			return;
		}
		enrollmentService.applyRuleEnrollment(patientId, model.getId(), selected.getRuleId(), selected.getAction(),
		    event.getSourceUuid(), event.getOccurredAt());
	}
}
