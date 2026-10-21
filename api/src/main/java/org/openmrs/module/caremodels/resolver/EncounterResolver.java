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

/** Actual encounter records, not observations masquerading as encounters. */
public class EncounterResolver implements DataResolver {

	@Override
	public String getId() {
		return "encounter-query";
	}

	@Override
	public String getReferenceKind() {
		return "encounterTypes";
	}

	@Override
	public String getReferenceField() {
		return "encounterTypeAlias";
	}

	@Override public JsonNode resolve(Integer patientId, JsonNode source, String reference) {
        Patient patient = ResolverSupport.patient(patientId);
        EncounterType type = reference == null ? null : Context.getEncounterService().getEncounterTypeByUuid(reference);
        if (type == null || type.isRetired()) throw new IllegalArgumentException("Encounter type unavailable");
        Instant now = Instant.now();
        int limit = ResolverSupport.limit(source, 100);
        Date since = ResolverSupport.since(source, now, 365);
        List<Encounter> safe = new ArrayList<>();
        for (Encounter encounter : Context.getEncounterService().getEncounters(patient, null, since, Date.from(now), null,
                Collections.singletonList(type), null, null, null, false)) {
            if (!Boolean.TRUE.equals(encounter.getVoided()) && patient.equals(encounter.getPatient())
                && type.equals(encounter.getEncounterType()) && ResolverSupport.inWindow(encounter.getEncounterDatetime(), since, now)) safe.add(encounter);
        }
        safe.sort(ResolverSupport.newest(Encounter::getEncounterDatetime, Encounter::getUuid));
        ObjectNode result = ResolverSupport.result(getId(), now);
        result.putArray("availableFields").add("uuid").add("effectiveTime").add("encounterTypeUuid").add("encounterTypeName").add("status");
        ArrayNode records = result.putArray("records");
        for (Encounter encounter : safe.subList(0, Math.min(limit, safe.size()))) {
            records.addObject().put("uuid", encounter.getUuid()).put("effectiveTime", ResolverSupport.time(encounter.getEncounterDatetime()))
                .put("encounterTypeUuid", type.getUuid()).put("encounterTypeName", type.getName()).put("status", "recorded");
        }
        ResolverSupport.collection(result, records, safe.size(), limit);
        return result;
    }
}
