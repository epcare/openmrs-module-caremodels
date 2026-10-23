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

/** Encounter diagnoses with certainty and encounter provenance, separate from conditions. */
public class DiagnosisResolver implements DataResolver {
	
	@Override
	public String getId() {
		return "encounter-diagnoses";
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
        Date since = ResolverSupport.since(source, now, 365);
        List<Diagnosis> safe = new ArrayList<>();
        for (Diagnosis diagnosis : Context.getDiagnosisService().getDiagnoses(patient, since)) {
            Encounter encounter = diagnosis.getEncounter();
            if (!Boolean.TRUE.equals(diagnosis.getVoided()) && patient.equals(diagnosis.getPatient()) && encounter != null
                && !Boolean.TRUE.equals(encounter.getVoided()) && patient.equals(encounter.getPatient())
                && ResolverSupport.inWindow(encounter.getEncounterDatetime(), since, now)) safe.add(diagnosis);
        }
        safe.sort(ResolverSupport.newest(d -> d.getEncounter().getEncounterDatetime(), Diagnosis::getUuid));
        ObjectNode result = ResolverSupport.result(getId(), now).put("scope", "encounter-diagnoses");
        result.putArray("availableFields").add("uuid").add("encounterUuid").add("effectiveTime").add("certainty").add("conceptUuid").add("name");
        ArrayNode records = result.putArray("records");
        for (Diagnosis diagnosis : safe.subList(0, Math.min(limit, safe.size()))) {
            ObjectNode row = records.addObject().put("uuid", diagnosis.getUuid()).put("encounterUuid", diagnosis.getEncounter().getUuid())
                .put("effectiveTime", ResolverSupport.time(diagnosis.getEncounter().getEncounterDatetime()))
                .put("certainty", diagnosis.getCertainty() == null ? null : diagnosis.getCertainty().name());
            ConditionResolver.coded(row, diagnosis.getDiagnosis());
        }
        ResolverSupport.collection(result, records, safe.size(), limit);
        return result;
    }
}
