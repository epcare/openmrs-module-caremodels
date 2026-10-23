/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import com.fasterxml.jackson.databind.JsonNode;

/** Filters a clinical event against enrollment triggers in one published model definition. */
public class EnrollmentTriggerMatcher {
	
	public boolean matches(JsonNode definition, ClinicalChangeEvent event) {
		if (definition == null || !definition.path("enrollment").path("enabled").asBoolean(false)) {
			return false;
		}
		JsonNode triggers = definition.path("enrollment").path("triggers");
		for (JsonNode trigger : triggers) {
			String referenceUuid = resolveReferenceUuid(definition.path("terminology"), trigger, event.getTriggerType());
			if (event.getTriggerType().name().equals(trigger.path("type").asText())
			        && (referenceUuid == null || event.getReferenceUuid().equals(referenceUuid))) {
				return true;
			}
		}
		return false;
	}
	
	/** Corrections also affect a model that subscribed only to the original save event. */
	public boolean matchesCorrection(JsonNode definition, ClinicalChangeEvent event) {
		String type = event.getTriggerType().name();
		if (!(type.endsWith("_CHANGED") || type.endsWith("_VOIDED")))
			return false;
		String family = type.substring(0, type.indexOf('_'));
		for (JsonNode trigger : definition.path("enrollment").path("triggers")) {
			String reference = resolveReferenceUuid(definition.path("terminology"), trigger, event.getTriggerType());
			if (trigger.path("type").asText().startsWith(family + "_")
			        && (reference == null || reference.equals(event.getReferenceUuid())))
				return true;
		}
		return false;
	}
	
	private String resolveReferenceUuid(JsonNode terminology, JsonNode trigger, ClinicalChangeEvent.TriggerType type) {
		String kind;
		String specificAlias;
		if (type.name().startsWith("PROGRAM_")) {
			kind = "programs";
			specificAlias = "programAlias";
		} else if (type.name().startsWith("ENCOUNTER_")) {
			kind = "encounterTypes";
			specificAlias = "encounterTypeAlias";
		} else {
			kind = "concepts";
			specificAlias = "conceptAlias";
		}
		String alias = trigger.path(specificAlias).asText(trigger.path("alias").asText(null));
		return alias == null ? null : terminology.path(kind).path(alias).asText("__undeclared__");
	}
}
