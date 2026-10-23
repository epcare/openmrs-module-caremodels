/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.resolver;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.openmrs.*;
import org.openmrs.api.context.Context;

/** Longitudinal conditions; does not relabel encounter diagnoses as conditions. */
public class ConditionResolver implements DataResolver {
	
	@Override
	public String getId() {
		return "conditions";
	}
	
	@Override
	public String getReferenceKind() {
		return null;
	}
	
	@Override
	public String getReferenceField() {
		return null;
	}
	
	@Override public JsonNode resolve(Integer patientId, JsonNode source, String unused) {
        Patient patient = ResolverSupport.patient(patientId);
        Instant now = Instant.now();
        int limit = ResolverSupport.limit(source, 100);
        Date since = ResolverSupport.since(source, now, 3650);
        List<Condition> all = Context.getConditionService().getAllConditions(patient);
        Set<String> superseded = new HashSet<>();
        for (Condition condition : all) {
            if (!Boolean.TRUE.equals(condition.getVoided()) && patient.equals(condition.getPatient()) && condition.getPreviousVersion() != null)
                superseded.add(condition.getPreviousVersion().getUuid());
        }
        List<Condition> safe = new ArrayList<>();
        for (Condition condition : all) {
            Date effective = condition.getOnsetDate() == null ? condition.getDateCreated() : condition.getOnsetDate();
            if (!Boolean.TRUE.equals(condition.getVoided()) && patient.equals(condition.getPatient()) && !superseded.contains(condition.getUuid())
                && ResolverSupport.inWindow(effective, since, now)) safe.add(condition);
        }
        safe.sort(ResolverSupport.newest(c -> c.getOnsetDate() == null ? c.getDateCreated() : c.getOnsetDate(), Condition::getUuid));
        ObjectNode result = ResolverSupport.result(getId(), now).put("scope", "longitudinal-conditions");
        result.putArray("availableFields").add("uuid").add("onsetDate").add("endDate").add("clinicalStatus").add("verificationStatus").add("conceptUuid").add("name");
        ArrayNode records = result.putArray("records");
        for (Condition condition : safe.subList(0, Math.min(limit, safe.size()))) {
            ObjectNode row = records.addObject().put("uuid", condition.getUuid()).put("onsetDate", ResolverSupport.time(condition.getOnsetDate()))
                .put("endDate", ResolverSupport.time(condition.getEndDate()))
                .put("clinicalStatus", condition.getClinicalStatus() == null ? null : condition.getClinicalStatus().name())
                .put("verificationStatus", condition.getVerificationStatus() == null ? null : condition.getVerificationStatus().name());
            coded(row, condition.getCondition());
        }
        ResolverSupport.collection(result, records, safe.size(), limit);
        return result;
    }
	
	static void coded(ObjectNode row, CodedOrFreeText value) {
		row.put("conceptUuid", value == null || value.getCoded() == null ? null : value.getCoded().getUuid());
		row.put("name", value == null ? null : value.getCoded() != null && value.getCoded().getName() != null ? value
		        .getCoded().getName().getName() : value.getNonCoded());
	}
}
