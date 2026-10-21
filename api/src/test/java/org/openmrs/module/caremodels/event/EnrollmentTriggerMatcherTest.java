/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EnrollmentTriggerMatcherTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private final EnrollmentTriggerMatcher matcher = new EnrollmentTriggerMatcher();

	@Test
	public void shouldMatchSpecificAliasResolvedToEventReferenceUuid() throws Exception {
		JsonNode definition = definition(true, "[{\"type\":\"OBSERVATION_SAVED\",\"conceptAlias\":\"blood_pressure\"}]");
		assertTrue(matcher.matches(definition, event(ClinicalChangeEvent.TriggerType.OBSERVATION_SAVED, "concept-uuid")));
		assertFalse(matcher.matches(definition, event(ClinicalChangeEvent.TriggerType.OBSERVATION_CHANGED, "concept-uuid")));
		assertFalse(matcher.matches(definition, event(ClinicalChangeEvent.TriggerType.OBSERVATION_SAVED, "other")));
	}

	@Test
	public void shouldMatchGenericAliasForProgramAndEncounterTriggers() throws Exception {
		JsonNode program = definition(true, "[{\"type\":\"PROGRAM_CHANGED\",\"alias\":\"hiv\"}]");
		JsonNode encounter = definition(true, "[{\"type\":\"ENCOUNTER_VOIDED\",\"alias\":\"visit\"}]");
		assertTrue(matcher.matches(program, event(ClinicalChangeEvent.TriggerType.PROGRAM_CHANGED, "program-uuid")));
		assertTrue(matcher.matches(encounter, event(ClinicalChangeEvent.TriggerType.ENCOUNTER_VOIDED, "encounter-uuid")));
	}

	@Test
	public void shouldIgnoreTriggersWhenEnrollmentIsDisabled() throws Exception {
		JsonNode definition = definition(false, "[{\"type\":\"OBSERVATION_SAVED\",\"conceptAlias\":\"blood_pressure\"}]");
		assertFalse(matcher.matches(definition, event(ClinicalChangeEvent.TriggerType.OBSERVATION_SAVED, "concept-uuid")));
	}

	@Test
	public void shouldTreatTypeOnlyTriggerAsWildcardForThatClinicalResource() throws Exception {
		JsonNode definition = definition(true, "[{\"type\":\"ENCOUNTER_SAVED\"}]");
		assertTrue(matcher.matches(definition, event(ClinicalChangeEvent.TriggerType.ENCOUNTER_SAVED, "any-type")));
		assertFalse(matcher.matches(definition, event(ClinicalChangeEvent.TriggerType.ENCOUNTER_CHANGED, "any-type")));
	}

	private JsonNode definition(boolean enabled, String triggers) throws Exception {
		return mapper.readTree("{\"terminology\":{\"concepts\":{\"blood_pressure\":\"concept-uuid\"},"
		        + "\"programs\":{\"hiv\":\"program-uuid\"},\"encounterTypes\":{\"visit\":\"encounter-uuid\"}},"
		        + "\"enrollment\":{\"enabled\":" + enabled + ",\"triggers\":" + triggers + "}}");
	}

	private ClinicalChangeEvent event(ClinicalChangeEvent.TriggerType type, String referenceUuid) {
		return new ClinicalChangeEvent(type, "patient", "source", referenceUuid, new Date());
	}
}
