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
import java.util.Date;
import java.util.function.Function;
import java.util.Comparator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;

/** Common bounds and provenance. No patient state is cached. */
public final class ResolverSupport {
	
	public static final ObjectMapper MAPPER = new ObjectMapper();
	
	public static final int MAX_RESULTS = 1000;
	
	private ResolverSupport() {
	}
	
	public static Patient patient(Integer id) {
		if (id == null)
			throw new IllegalArgumentException("Authorized patient id is required");
		Patient patient = Context.getPatientService().getPatient(id);
		if (patient == null || Boolean.TRUE.equals(patient.getVoided()))
			throw new IllegalArgumentException("Patient unavailable");
		return patient;
	}
	
	public static int limit(JsonNode source, int defaultLimit) {
		int limit = source.path("maxResults").asInt(defaultLimit);
		if (limit < 1 || limit > MAX_RESULTS)
			throw new IllegalArgumentException("maxResults must be 1..1000");
		if (source.path("includeVoided").asBoolean(false))
			throw new IllegalArgumentException("Voided records are forbidden");
		return limit;
	}
	
	public static Date since(JsonNode source, Instant now, int defaultDays) {
		int days = source.path("windowDays").asInt(defaultDays);
		if (days < 0 || days > 3650 || (source.has("windowDays") && days == 0))
			throw new IllegalArgumentException("Invalid windowDays");
		return days == 0 ? null : Date.from(now.minusSeconds(days * 86400L));
	}
	
	public static String time(Date value) {
		return value == null ? null : Instant.ofEpochMilli(value.getTime()).toString();
	}
	
	public static boolean inWindow(Date value, Date since, Instant now) {
		return value != null && value.getTime() <= now.toEpochMilli() && (since == null || !value.before(since));
	}
	
	public static <T> Comparator<T> newest(Function<T, Date> date, Function<T, String> uuid) {
		return Comparator.comparing(date, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(uuid,
		    Comparator.nullsLast(Comparator.naturalOrder()));
	}
	
	public static ObjectNode result(String type, Instant now) {
		ObjectNode result = MAPPER.createObjectNode().put("type", type).put("resolverVersion", "1.0.0")
		        .put("assessedAt", now.toString());
		result.put("provenance", "OpenMRS service API");
		return result;
	}
	
	public static void collection(ObjectNode result, ArrayNode records, int count, int limit) {
		result.put("status", count == 0 ? "no-records" : "ok");
		result.put("returnedCount", records.size()).put("truncated", count > limit);
		result.putNull("total"); // never pretend the bounded return is the entire history
	}
}
