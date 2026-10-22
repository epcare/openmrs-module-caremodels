/** This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. */
package org.openmrs.module.caremodels.rule;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Decision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnrollmentRuleEngineTest {

	private ObjectMapper mapper;

	private EnrollmentRuleEngine engine;

	private RuleEvidenceProvider provider;

	@Before
	public void setup() {
		mapper = new ObjectMapper();
		engine = new EnrollmentRuleEngine();
		provider = new RuleEvidenceProvider() {

			@Override
			public RuleEvidence resolve(String type, String alias) {
				return RuleEvidence.unknown();
			}
		};
	}

	@Test
	public void shouldPreferExclusionRegardlessOfDefinitionOrder() throws Exception {
		Decision decision = engine.evaluate(rules("[{\"id\":\"auto\",\"action\":\"AUTO_ENROLL\","
		        + "\"expression\":{\"literal\":\"TRUE\"}},{\"id\":\"exclude\",\"action\":\"EXCLUDE\","
		        + "\"expression\":{\"literal\":\"TRUE\"}}]"), provider, new Date(0));
		assertTrue(decision.hasMatch());
		assertEquals("exclude", decision.getSelected().getRuleId());
		assertEquals(EnrollmentRuleEngine.Action.EXCLUDE, decision.getSelected().getAction());
	}

	@Test
	public void shouldUseLexicalRuleIdAsStableTieBreaker() throws Exception {
		Decision decision = engine.evaluate(rules("[{\"id\":\"z-rule\",\"action\":\"AUTO_ENROLL\","
		        + "\"expression\":{\"literal\":\"TRUE\"}},{\"id\":\"a-rule\",\"action\":\"AUTO_ENROLL\","
		        + "\"expression\":{\"literal\":\"TRUE\"}}]"), provider, new Date(0));
		assertEquals("a-rule", decision.getSelected().getRuleId());
	}

	@Test
	public void confirmationShouldTakePrecedenceOverAutomaticEnrollment() throws Exception {
		Decision decision = engine.evaluate(rules("[{\"id\":\"auto\",\"action\":\"AUTO_ENROLL\","
		        + "\"expression\":{\"literal\":\"TRUE\"}},{\"id\":\"confirm\",\"action\":\"PROPOSE_CONFIRMATION\","
		        + "\"expression\":{\"literal\":\"TRUE\"}}]"), provider, new Date(0));
		assertEquals("confirm", decision.getSelected().getRuleId());
	}

	@Test
	public void unknownAndFalseRulesShouldNotProduceDecision() throws Exception {
		Decision decision = engine.evaluate(rules("[{\"id\":\"unknown\",\"action\":\"AUTO_ENROLL\","
		        + "\"expression\":{\"type\":\"observation\",\"operator\":\"EXISTS\",\"alias\":\"x\"}},"
		        + "{\"id\":\"false\",\"action\":\"EXCLUDE\",\"expression\":{\"literal\":\"FALSE\"}}]"), provider,
		    new Date(0));
		assertFalse(decision.hasMatch());
		assertEquals(2, decision.getEvaluations().size());
		assertEquals(TriState.UNKNOWN, decision.getEvaluations().get(0).getResult());
	}

	private JsonNode rules(String value) throws Exception {
		return mapper.readTree(value);
	}
}
