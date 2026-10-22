/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.openmrs.module.caremodels.api.CareModelEnrollmentService;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Action;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Decision;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.RuleEvaluation;
import org.openmrs.module.caremodels.rule.RuleEvidenceProvider;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Evaluates a committed clinical change against every matching published Care Model. */
public class ClinicalChangeEnrollmentProcessor implements ClinicalChangeListener {

	private static final Logger log = Logger.getLogger(ClinicalChangeEnrollmentProcessor.class.getName());

	private CareModelDAO careModelDAO;

	private EnrollmentTriggerMatcher triggerMatcher;

	private EnrollmentRuleEngine ruleEngine;

	private EnrollmentRuleEvidenceFactory evidenceFactory;

	private CareModelEnrollmentService enrollmentService;

	private ObjectMapper objectMapper = new ObjectMapper();

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

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onClinicalChange(ClinicalChangeEvent event) {
		if (event == null) {
			return;
		}
		Integer patientId = evidenceFactory.resolvePatientId(event.getPatientUuid());
		if (patientId == null) {
			log.warning("Ignoring clinical change for missing patient " + event.getPatientUuid());
			return;
		}
		if (event.getReviewSourceUuid() != null) {
			try {
				enrollmentService.flagEnrollmentsForReview(patientId, event.getReviewSourceUuid(),
				    reviewReason(event.getTriggerType()), event.getOccurredAt());
			}
			catch (RuntimeException e) {
				log.log(Level.SEVERE, "Failed to flag corrected or voided enrollment source " + event.getReviewSourceUuid(),
				    e);
			}
		}

		List<CareModel> models = careModelDAO.getPublishedCareModelsForEnrollment();
		for (CareModel model : models) {
			try {
				evaluateModel(patientId, model, event);
			}
			catch (RuntimeException e) {
				log.log(Level.SEVERE, "Enrollment evaluation failed for Care Model " + model.getCode() + " and source "
				        + event.getSourceUuid(), e);
			}
		}
	}

	private String reviewReason(ClinicalChangeEvent.TriggerType triggerType) {
		return "Enrollment source evidence was corrected or voided (" + triggerType.name() + ")";
	}

	private void evaluateModel(Integer patientId, CareModel model, ClinicalChangeEvent event) {
		StorageEnvelope envelope = StorageEnvelope.fromJson(objectMapper, model.getConfiguration());
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
