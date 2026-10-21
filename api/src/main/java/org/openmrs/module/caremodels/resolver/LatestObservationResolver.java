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

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.openmrs.Concept;
import org.openmrs.ConceptDatatype;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;

/**
 * Latest non-voided observation for one concept. Bounded (maxResults), excludes voided data, and
 * returns typed values with provenance (observation uuid, effective time, units).
 */
public class LatestObservationResolver implements DataResolver {
	
	@Override
	public String getId() {
		return "latest-observation";
	}
	
	@Override
	public JsonNode resolve(Integer patientId, JsonNode source, String resolvedConceptUuid) {
		ObjectMapperHolder mapper = ObjectMapperHolder.INSTANCE;
		ObjectNode result = mapper.get().createObjectNode();
		result.put("type", getId());
		result.put("concept", resolvedConceptUuid);
		
		if (resolvedConceptUuid == null) {
			result.put("value", (String) null);
			return result;
		}
		
		Concept concept = Context.getConceptService().getConceptByUuid(resolvedConceptUuid);
		if (concept == null) {
			throw new IllegalArgumentException("Concept not found: " + resolvedConceptUuid);
		}
		
		int maxResults = source.path("maxResults").asInt(1);
		int windowDays = source.path("windowDays").asInt(0);
		
		List<Obs> obsList = Context.getObsService().getObservations(
		    Collections.singletonList(Context.getPersonService().getPerson(patientId)), null,
		    Collections.singletonList(concept), null, null, null, null, maxResults, null, null, null, false);
		
		ArrayNode values = result.putArray("values");
		int returned = 0;
		for (Obs obs : obsList) {
			if (windowDays > 0 && obs.getObsDatetime() != null
			        && obs.getObsDatetime().before(new java.util.Date(System.currentTimeMillis() - windowDays * 86400000L))) {
				continue;
			}
			ObjectNode value = values.addObject();
			value.put("obsUuid", obs.getUuid());
			value.put("effectiveTime", obs.getObsDatetime() != null ? obs.getObsDatetime().toString() : null);
			if (obs.getValueNumeric() != null) {
				value.put("valueType", "NUMBER");
				value.put("value", obs.getValueNumeric());
			} else if (obs.getValueCoded() != null) {
				value.put("valueType", "CODED");
				value.put("value", obs.getValueCoded().getUuid());
				value.put("valueName", obs.getValueCoded().getName().getName());
			} else if (obs.getValueText() != null) {
				value.put("valueType", "STRING");
				value.put("value", obs.getValueText());
			} else if (obs.getValueDatetime() != null) {
				value.put("valueType", "DATE");
				value.put("value", obs.getValueDatetime().toString());
			}
			returned++;
			if (returned >= maxResults) {
				break;
			}
		}
		return result;
	}
	
	/**
	 * Shared lazy mapper holder; resolvers must not hold per-request state.
	 */
	private static class ObjectMapperHolder {
		
		private static final ObjectMapperHolder INSTANCE = new ObjectMapperHolder();
		
		private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		
		com.fasterxml.jackson.databind.ObjectMapper get() {
			return mapper;
		}
	}
}
