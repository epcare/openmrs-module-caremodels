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
import org.openmrs.Patient;

/** Explicitly selected, minimal person fields; no identifiers, addresses or attributes. */
public class PatientSummaryResolver implements DataResolver {
    public static final Set<String> FIELDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("name", "gender", "birthdate", "birthdateEstimated", "dead", "deathDate")));
    @Override public String getId() { return "patient-summary"; }
    @Override public String getReferenceKind() { return null; }
    @Override public String getReferenceField() { return null; }
    @Override public JsonNode resolve(Integer patientId, JsonNode source, String unused) {
        if (!source.path("fields").isArray() || source.path("fields").size() == 0) throw new IllegalArgumentException("Explicit patient fields required");
        for (JsonNode field : source.path("fields")) if (!FIELDS.contains(field.asText())) throw new IllegalArgumentException("Unsupported patient field");
        Patient patient = ResolverSupport.patient(patientId);
        ObjectNode result = ResolverSupport.result(getId(), Instant.now()).put("status", "ok");
        ObjectNode fields = result.putObject("fields");
        for (JsonNode field : source.path("fields")) {
            switch (field.asText()) {
                case "name": fields.put("name", patient.getPersonName() == null ? null : patient.getPersonName().getFullName()); break;
                case "gender": fields.put("gender", patient.getGender()); break;
                case "birthdate": fields.put("birthdate", ResolverSupport.time(patient.getBirthdate())); break;
                case "birthdateEstimated": fields.put("birthdateEstimated", patient.getBirthdateEstimated()); break;
                case "dead": fields.put("dead", patient.getDead()); break;
                case "deathDate": fields.put("deathDate", ResolverSupport.time(patient.getDeathDate())); break;
                default: throw new IllegalArgumentException("Unsupported patient field");
            }
        }
        return result;
    }
}
