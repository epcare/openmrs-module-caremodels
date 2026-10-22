/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.api.impl;

import java.util.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openmrs.*;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.caremodels.api.*;
import org.openmrs.module.caremodels.api.db.*;
import org.openmrs.module.caremodels.model.*;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.event.EnrollmentRuleEvidenceFactory;
import org.openmrs.module.caremodels.rule.*;
import org.springframework.transaction.annotation.Transactional;

/** One model and at most 100 patient IDs per transaction; checkpoint reuses core global_property. */
public class CareModelReconciliationServiceImpl extends BaseOpenmrsService implements CareModelReconciliationService {

	private final ObjectMapper mapper = new ObjectMapper();

	private CareModelDAO modelDAO;

	private CareModelMemberDAO memberDAO;

	private CareModelEnrollmentService enrollment;

	private EnrollmentRuleEvidenceFactory evidence;

	public void setCareModelDAO(CareModelDAO dao) {
		modelDAO = dao;
	}

	public void setMemberDAO(CareModelMemberDAO dao) {
		memberDAO = dao;
	}

	public void setEnrollmentService(CareModelEnrollmentService service) {
		enrollment = service;
	}

	public void setEvidenceFactory(EnrollmentRuleEvidenceFactory factory) {
		evidence = factory;
	}

	@Override @Transactional(timeout = 30)
    public int reconcileNextBatch(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Batch size must be 1..100");
        org.openmrs.api.AdministrationService admin = Context.getAdministrationService();
        GlobalProperty checkpoint = modelDAO.getReconciliationCursorForUpdate();
        if (checkpoint == null) {
            admin.saveGlobalProperty(new GlobalProperty("caremodels.reconciliation.cursor", "{}"));
            checkpoint = modelDAO.getReconciliationCursorForUpdate();
        }
        if (checkpoint == null) throw new APIException("Reconciliation checkpoint unavailable");
        try {
            JsonNode stored = mapper.readTree(checkpoint.getPropertyValue());
            if (stored == null || !stored.isObject()) throw new APIException("Invalid reconciliation checkpoint");
            ObjectNode cursor = (ObjectNode) stored;
            int modelId = cursor.path("modelId").asInt(0);
            CareModel model = modelId == 0 || cursor.path("completed").asBoolean() ? null : modelDAO.getCareModelById(modelId);
            if (model == null || Boolean.TRUE.equals(model.getRetired()) || model.getStatus() != CareModel.CareModelStatus.PUBLISHED) {
                List<CareModel> models = modelDAO.getEnrollmentModelsAfter(modelId, 1);
                if (models.isEmpty()) { checkpoint.setPropertyValue("{}"); admin.saveGlobalProperty(checkpoint); return 0; }
                model = models.get(0); cursor = mapper.createObjectNode().put("modelId", model.getId());
            }
            StorageEnvelope.PublishedSnapshot published = StorageEnvelope.fromJson(mapper, model.getConfiguration()).getPublished();
            if (published == null) throw new APIException("Published model snapshot unavailable");
            if (!published.getChecksum().equals(cursor.path("checksum").asText())) {
                cursor.put("patientId", 0).put("checksum", published.getChecksum()).put("asOf", System.currentTimeMillis())
                    .put("upperPatientId", modelDAO.getMaximumPatientId()).put("completed", false);
            }
            Date asOf = new Date(cursor.path("asOf").asLong());
            List<Integer> patients = modelDAO.getPatientIdsAfter(cursor.path("patientId").asInt(), cursor.path("upperPatientId").asInt(), limit);
            for (Integer patientId : patients) {
                reconcile(patientId, model, published, asOf);
                cursor.put("patientId", patientId);
            }
            if (patients.size() < limit) cursor.put("completed", true);
            checkpoint.setPropertyValue(cursor.toString()); admin.saveGlobalProperty(checkpoint);
            return patients.size();
        } catch (java.io.IOException | ClassCastException e) { throw new APIException("Invalid reconciliation checkpoint; reset it explicitly", e); }
    }

	private void reconcile(Integer patientId, CareModel model, StorageEnvelope.PublishedSnapshot published, Date asOf) {
        JsonNode definition = published.getDefinition();
        if (!definition.path("enrollment").path("enabled").asBoolean()) return;
        RuleEvidenceProvider provider = evidence.create(patientId, definition, asOf);
        final boolean[] unavailable = { false };
        RuleEvidenceProvider checked = (type, alias) -> {
            try {
                RuleEvidence value = provider.resolve(type, alias);
                if (value == null || value.getAvailability() == RuleEvidence.Availability.UNKNOWN) unavailable[0] = true;
                return value == null ? RuleEvidence.unknown() : value;
            } catch (RuntimeException e) { unavailable[0] = true; return RuleEvidence.unknown(); }
        };
        EnrollmentRuleEngine.Decision decision = new EnrollmentRuleEngine().evaluate(definition.path("enrollment").path("rules"), checked, asOf);
        // Known absence may leave a numeric predicate UNKNOWN; that is not a transport failure.
        // Unavailable services/references must retry instead of silently skipping a patient page.
        if (unavailable[0]) throw new APIException("Enrollment evidence unavailable; recovery batch will retry");
        CareModelMember current = memberDAO.getOpenMember(patientId, model.getId());
        if (decision.hasMatch() && decision.getSelected().getAction() != EnrollmentRuleEngine.Action.EXCLUDE) {
            int next = memberDAO.getNextEpisodeNumber(patientId, model.getId());
            String source = UUID.nameUUIDFromBytes((patientId + ":" + model.getId() + ":" + published.getChecksum() + ":" + next).getBytes(StandardCharsets.UTF_8)).toString();
            enrollment.applyRuleEnrollment(patientId, model.getId(), decision.getSelected().getRuleId(), decision.getSelected().getAction(), source, asOf);
        } else if (current != null && !"MANUAL".equals(current.getEnrollmentSource())) {
            String source = UUID.nameUUIDFromBytes(("review:" + current.getUuid() + ":" + published.getChecksum()).getBytes(StandardCharsets.UTF_8)).toString();
            enrollment.flagForReview(patientId, model.getId(), source, asOf);
        }
    }
}
