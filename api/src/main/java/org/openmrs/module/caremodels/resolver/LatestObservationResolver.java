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

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openmrs.*;
import org.openmrs.api.context.Context;

/** Bounded observation values. Keeps the original values[] wire contract. */
public class LatestObservationResolver implements DataResolver {

	private final String id;

	private final Clock clock;

	public LatestObservationResolver() {
		this("latest-observation", Clock.systemUTC());
	}

	public LatestObservationResolver(String id, Clock clock) {
		this.id = id;
		this.clock = clock;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override public JsonNode resolve(Integer patientId, JsonNode source, String reference) {
        Patient patient = ResolverSupport.patient(patientId);
        if (reference == null) throw new IllegalArgumentException("A declared concept alias is required");
        Concept concept = Context.getConceptService().getConceptByUuid(reference);
        if (concept == null || concept.isRetired()) throw new IllegalArgumentException("Concept unavailable");
        Instant now = clock.instant();
        int limit = ResolverSupport.limit(source, id.equals("latest-observation") ? 1 : 100);
        Date since = ResolverSupport.since(source, now, id.equals("latest-observation") ? 0 : 365);
        List<Obs> observations = Context.getObsService().getObservations(Collections.singletonList(patient), null,
            Collections.singletonList(concept), null, null, null, Arrays.asList("obsDatetime desc", "uuid asc"),
            limit + 1, null, since, Date.from(now), false);
        List<Obs> safe = new ArrayList<>();
        for (Obs obs : observations) {
            if (!Boolean.TRUE.equals(obs.getVoided()) && patient.getId().equals(obs.getPersonId())
                    && concept.equals(obs.getConcept()) && ResolverSupport.inWindow(obs.getObsDatetime(), since, now)) safe.add(obs);
        }
        safe.sort(ResolverSupport.newest(Obs::getObsDatetime, Obs::getUuid));
        ObjectNode result = ResolverSupport.result(id, now).put("concept", reference);
        if (concept instanceof ConceptNumeric) result.put("units", ((ConceptNumeric) concept).getUnits());
        ArrayNode values = result.putArray("values");
        for (Obs obs : safe.subList(0, Math.min(limit, safe.size()))) {
            ObjectNode value = values.addObject().put("obsUuid", obs.getUuid())
                .put("effectiveTime", ResolverSupport.time(obs.getObsDatetime()));
            String modifier = obs.getValueModifier();
            if (obs.getValueNumeric() != null && modifier != null && !modifier.trim().isEmpty() && !"=".equals(modifier)) {
                value.put("valueType", "STRING").put("value", modifier + obs.getValueNumeric());
            } else if (obs.getValueNumeric() != null && Double.isFinite(obs.getValueNumeric())) {
                value.put("valueType", "NUMBER").put("value", obs.getValueNumeric());
            } else if (obs.getValueCoded() != null) {
                value.put("valueType", "CODED").put("value", obs.getValueCoded().getUuid());
                if (obs.getValueCoded().getName() != null) value.put("valueName", obs.getValueCoded().getName().getName());
            } else if (obs.getValueText() != null) {
                value.put("valueType", "STRING").put("value", obs.getValueText());
            } else if (obs.getValueDatetime() != null) {
                value.put("valueType", "DATE").put("value", ResolverSupport.time(obs.getValueDatetime()));
            } else { value.put("valueType", "STRING").putNull("value"); }
        }
        ResolverSupport.collection(result, values, safe.size(), limit);
        return result;
    }
}
