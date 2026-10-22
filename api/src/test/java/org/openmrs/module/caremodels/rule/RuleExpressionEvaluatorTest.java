/** This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. */
package org.openmrs.module.caremodels.rule;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RuleExpressionEvaluatorTest {

	private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

	private final Date asOf = new Date(10 * DAY_MILLIS);

	private final Map<String, RuleEvidence> evidence = new HashMap<String, RuleEvidence>();

	private ObjectMapper mapper;

	private RuleExpressionEvaluator evaluator;

	private RuleEvidenceProvider provider;

	@Before
	public void setup() {
		mapper = new ObjectMapper();
		evaluator = new RuleExpressionEvaluator();
		provider = new RuleEvidenceProvider() {

			@Override
			public RuleEvidence resolve(String type, String alias) {
				RuleEvidence resolved = evidence.get(type + ":" + alias);
				return resolved == null ? RuleEvidence.unknown() : resolved;
			}
		};
	}

	@Test
	public void shouldPreserveUnknownThroughNestedExpressions() throws Exception {
		JsonNode expression = json("{\"all\":[{\"literal\":\"TRUE\"},{\"not\":{\"type\":\"observation\","
		        + "\"operator\":\"EQUALS\",\"alias\":\"missing\",\"value\":1}}]}");
		assertEquals(TriState.UNKNOWN, evaluator.evaluate(expression, provider, asOf));
	}

	@Test
	public void falseShouldShortCircuitUnknownInAll() throws Exception {
		JsonNode expression = json("{\"all\":[{\"type\":\"observation\",\"operator\":\"EXISTS\","
		        + "\"alias\":\"missing\"},{\"literal\":\"FALSE\"}]}");
		assertEquals(TriState.FALSE, evaluator.evaluate(expression, provider, asOf));
	}

	@Test
	public void shouldDistinguishKnownAbsenceFromUnknown() throws Exception {
		evidence.put("observation:weight", RuleEvidence.absent());
		assertEquals(TriState.FALSE, evaluator.evaluate(
		    json("{\"type\":\"observation\",\"operator\":\"EXISTS\",\"alias\":\"weight\"}"), provider, asOf));
		assertEquals(TriState.TRUE, evaluator.evaluate(
		    json("{\"type\":\"observation\",\"operator\":\"NOT_EXISTS\",\"alias\":\"weight\"}"), provider, asOf));
		assertEquals(TriState.UNKNOWN, evaluator.evaluate(
		    json("{\"type\":\"observation\",\"operator\":\"EXISTS\",\"alias\":\"missing\"}"), provider, asOf));
	}

	@Test
	public void shouldEvaluateNumericComparisonAndUnits() throws Exception {
		evidence.put("observation:bp", RuleEvidence.present(145, asOf, "mmHg"));
		assertEquals(TriState.TRUE, evaluator.evaluate(json("{\"type\":\"observation\",\"operator\":\"GREATER_OR_EQUAL\","
		        + "\"alias\":\"bp\",\"value\":140,\"unit\":\"mmHg\"}"), provider, asOf));
		assertEquals(TriState.UNKNOWN, evaluator.evaluate(
		    json("{\"type\":\"observation\",\"operator\":\"GREATER_OR_EQUAL\","
		            + "\"alias\":\"bp\",\"value\":140,\"unit\":\"kPa\"}"), provider, asOf));
		evidence.put("observation:bp", RuleEvidence.present(145, new Date(asOf.getTime() - 91 * DAY_MILLIS), "mmHg"));
		assertEquals(TriState.FALSE, evaluator.evaluate(json("{\"type\":\"observation\","
		        + "\"operator\":\"GREATER_OR_EQUAL\",\"alias\":\"bp\",\"value\":140,\"unit\":\"mmHg\","
		        + "\"windowDays\":90}"), provider, asOf));
	}

	@Test
	public void shouldEvaluateCodedAliasAndProgramState() throws Exception {
		evidence.put("observation:status", RuleEvidence.present("positive"));
		evidence.put("program:hiv", RuleEvidence.present("ON_ART"));
		assertEquals(TriState.TRUE, evaluator.evaluate(
		    json("{\"type\":\"observation\",\"operator\":\"EQUALS\",\"alias\":\"status\","
		            + "\"value\":\"ignored\",\"codedAnswerAlias\":\"positive\"}"), provider, asOf));
		assertEquals(TriState.TRUE,
		    evaluator.evaluate(json("{\"type\":\"program\",\"operator\":\"STATE_EQUALS\",\"alias\":\"hiv\","
		            + "\"value\":\"ON_ART\"}"), provider, asOf));
	}

	@Test
	public void shouldEvaluateWindowsAgainstSuppliedClock() throws Exception {
		evidence.put("encounter:visit", RuleEvidence.present("visit", new Date(asOf.getTime() - 5 * DAY_MILLIS), null));
		assertEquals(TriState.TRUE, evaluator.evaluate(
		    json("{\"type\":\"encounter\",\"operator\":\"WITHIN_WINDOW\",\"alias\":\"visit\","
		            + "\"value\":true,\"windowDays\":7}"), provider, asOf));
		assertEquals(TriState.FALSE, evaluator.evaluate(
		    json("{\"type\":\"encounter\",\"operator\":\"OUTSIDE_WINDOW\",\"alias\":\"visit\","
		            + "\"value\":true,\"windowDays\":7}"), provider, asOf));
		assertEquals(TriState.UNKNOWN, evaluator.evaluate(
		    json("{\"type\":\"encounter\",\"operator\":\"WITHIN_WINDOW\",\"alias\":\"visit\","
		            + "\"value\":true,\"windowDays\":7}"), provider, null));
	}

	@Test
	public void providerFailureShouldFailClosedAsUnknown() throws Exception {
		RuleEvidenceProvider failing = new RuleEvidenceProvider() {

			@Override
			public RuleEvidence resolve(String type, String alias) {
				throw new IllegalStateException("lookup failed");
			}
		};
		assertEquals(TriState.UNKNOWN,
		    evaluator.evaluate(json("{\"type\":\"observation\",\"operator\":\"EXISTS\",\"alias\":\"x\"}"), failing, asOf));
	}

	private JsonNode json(String value) throws Exception {
		return mapper.readTree(value);
	}

	@Test
	public void mismatchedTypesAndNonFiniteNumbersMustRemainUnknown() throws Exception {
		for (Object value : new Object[] { "50", "<50", Double.NaN, Double.POSITIVE_INFINITY }) {
			evidence.put("observation:measurement", RuleEvidence.present(value));
			assertEquals(TriState.UNKNOWN, evaluator.evaluate(
			    json("{\"type\":\"observation\",\"alias\":\"measurement\",\"operator\":\"LESS_THAN\",\"value\":100}"),
			    provider, asOf));
			assertEquals(TriState.UNKNOWN, evaluator.evaluate(
			    json("{\"type\":\"observation\",\"alias\":\"measurement\",\"operator\":\"NOT_EQUALS\",\"value\":100}"),
			    provider, asOf));
		}
	}
}
