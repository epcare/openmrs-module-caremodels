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

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.openmrs.module.caremodels.config.ValidationResult;

/** Executable source declarations; schema enums alone never enable a resolver. */
public final class SourceContract {

	private static final org.openmrs.module.caremodels.config.JsonSchemaValidator SCHEMAS = new org.openmrs.module.caremodels.config.JsonSchemaValidator();

	private SourceContract() {
	}

	public static String resolverId(JsonNode source) {
		return "registered".equals(source.path("type").asText()) ? source.path("resolverId").asText() : source.path("type")
		        .asText();
	}

	public static ValidationResult validate(JsonNode source, DataResolverRegistry registry, String path) {
        ValidationResult result = SCHEMAS.validate(source, org.openmrs.module.caremodels.config.JsonSchemaValidator.SchemaType.DATA_SOURCE);
        if (!result.isValid()) return result;
        String id = resolverId(source);
        DataResolver resolver = registry.getResolver(id);
        if (resolver == null) {
            result.addError(path, "Unavailable resolver: " + id);
            return result;
        }
        boolean registered = "registered".equals(source.path("type").asText());
        if (registered && !Arrays.asList("patient-summary", "conditions", "encounter-diagnoses").contains(id))
            result.addError(path + ".resolverId", "Use the built-in source type for this resolver");
        if (registered && !"1.0.0".equals(source.path("resolverVersion").asText()))
            result.addError(path + ".resolverVersion", "Unsupported resolver version");
        String kind = resolver.getReferenceField();
        Set<String> allowed = new HashSet<>(Arrays.asList("type", "resultType", "maxResults", "windowDays", "includeVoided"));
        if (kind != null) {
            allowed.add(kind);
            if (!source.hasNonNull(kind) || source.path(kind).asText().isEmpty()) result.addError(path + "." + kind, "Resource alias required");
        }
        if (registered) allowed.addAll(Arrays.asList("resolverId", "resolverVersion"));
        if (id.equals("patient-summary")) allowed.add("fields");
        if (id.equals("latest-observation") || id.equals("historical-observations")) allowed.addAll(Arrays.asList("units", "effectiveTime"));
        Iterator<String> fields = source.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) result.addError(path + "." + field, "Unsupported source option");
        }
        if (source.has("effectiveTime") && !"OBS_DATETIME".equals(source.path("effectiveTime").asText()))
            result.addError(path + ".effectiveTime", "Only OBS_DATETIME is implemented");
        if (!id.equals("latest-observation") && !id.equals("historical-observations") && !"TABLE".equals(source.path("resultType").asText()))
            result.addError(path + ".resultType", "Typed records require TABLE");
        if (id.equals("patient-summary")) {
            if (!source.path("fields").isArray() || source.path("fields").size() == 0) result.addError(path + ".fields", "Explicit fields required");
            for (JsonNode field : source.path("fields")) if (!PatientSummaryResolver.FIELDS.contains(field.asText())) result.addError(path + ".fields", "Unsupported patient field");
            if (source.has("windowDays") || source.has("maxResults")) result.addError(path, "Patient summary does not accept collection options");
        }
        try { ResolverSupport.limit(source, 100); ResolverSupport.since(source, java.time.Instant.now(), 365); }
        catch (IllegalArgumentException e) { result.addError(path, e.getMessage()); }
        return result;
    }
}
