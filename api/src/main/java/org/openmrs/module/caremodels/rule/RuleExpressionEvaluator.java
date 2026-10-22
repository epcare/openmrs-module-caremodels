/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.rule;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;

import org.openmrs.module.caremodels.rule.RuleEvidence.Availability;

/** Evaluates a structurally validated rule-expression tree using strong Kleene logic. */
public class RuleExpressionEvaluator {

	private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

	public TriState evaluate(JsonNode expression, RuleEvidenceProvider evidenceProvider, Date asOf) {
		if (expression == null || expression.isMissingNode() || expression.isNull() || evidenceProvider == null) {
			return TriState.UNKNOWN;
		}
		if (expression.has("literal")) {
			String literal = expression.path("literal").asText();
			return "TRUE".equals(literal) ? TriState.TRUE : "FALSE".equals(literal) ? TriState.FALSE : TriState.UNKNOWN;
		}
		if (expression.has("not")) {
			return evaluate(expression.path("not"), evidenceProvider, asOf).not();
		}
		if (expression.has("all")) {
			if (expression.path("all").size() == 0) {
				return TriState.UNKNOWN;
			}
			TriState result = TriState.TRUE;
			for (JsonNode child : expression.path("all")) {
				result = result.and(evaluate(child, evidenceProvider, asOf));
				if (result == TriState.FALSE) {
					return result;
				}
			}
			return result;
		}
		if (expression.has("any")) {
			if (expression.path("any").size() == 0) {
				return TriState.UNKNOWN;
			}
			TriState result = TriState.FALSE;
			for (JsonNode child : expression.path("any")) {
				result = result.or(evaluate(child, evidenceProvider, asOf));
				if (result == TriState.TRUE) {
					return result;
				}
			}
			return result;
		}
		return evaluatePredicate(expression, evidenceProvider, asOf);
	}

	private TriState evaluatePredicate(JsonNode predicate, RuleEvidenceProvider evidenceProvider, Date asOf) {
		String type = predicate.path("type").asText(null);
		String alias = predicate.path("alias").asText(null);
		String operator = predicate.path("operator").asText(null);
		if (type == null || operator == null) {
			return TriState.UNKNOWN;
		}
		RuleEvidence evidence;
		try {
			evidence = evidenceProvider.resolve(type, alias);
		}
		catch (RuntimeException e) {
			return TriState.UNKNOWN;
		}
		if (evidence == null || evidence.getAvailability() == Availability.UNKNOWN) {
			return TriState.UNKNOWN;
		}
		if ("EXISTS".equals(operator) || "NOT_EXISTS".equals(operator)) {
			TriState exists = evidence.getAvailability() == Availability.PRESENT ? TriState.TRUE : TriState.FALSE;
			return "NOT_EXISTS".equals(operator) ? exists.not() : exists;
		}
		if (evidence.getAvailability() != Availability.PRESENT) {
			return TriState.UNKNOWN;
		}
		if (predicate.has("unit")
		        && (evidence.getUnit() == null || !predicate.path("unit").asText().equals(evidence.getUnit()))) {
			return TriState.UNKNOWN;
		}
		if ("WITHIN_WINDOW".equals(operator) || "OUTSIDE_WINDOW".equals(operator)) {
			return evaluateWindow(evidence, predicate.path("windowDays").asInt(0), asOf, "OUTSIDE_WINDOW".equals(operator));
		}
		if (predicate.has("windowDays")) {
			TriState fresh = evaluateWindow(evidence, predicate.path("windowDays").asInt(0), asOf, false);
			if (fresh != TriState.TRUE) {
				return fresh;
			}
		}

		JsonNode expected = predicate.has("codedAnswerAlias") ? predicate.path("codedAnswerAlias") : predicate.path("value");
		if ("EQUALS".equals(operator) || "STATE_EQUALS".equals(operator) || "NOT_EQUALS".equals(operator)) {
			TriState equals = valuesEqual(evidence.getValue(), expected);
			return "NOT_EQUALS".equals(operator) ? equals.not() : equals;
		}
		if ("GREATER_THAN".equals(operator) || "LESS_THAN".equals(operator) || "GREATER_OR_EQUAL".equals(operator)
		        || "LESS_OR_EQUAL".equals(operator)) {
			return compareNumbers(evidence.getValue(), expected, operator);
		}
		return TriState.UNKNOWN;
	}

	private TriState evaluateWindow(RuleEvidence evidence, int windowDays, Date asOf, boolean outside) {
		Date effectiveTime = evidence.getEffectiveTime();
		if (effectiveTime == null || asOf == null || windowDays < 1) {
			return TriState.UNKNOWN;
		}
		long age = asOf.getTime() - effectiveTime.getTime();
		if (age < 0) {
			return TriState.UNKNOWN;
		}
		TriState within = age <= windowDays * DAY_MILLIS ? TriState.TRUE : TriState.FALSE;
		return outside ? within.not() : within;
	}

	private TriState valuesEqual(Object actual, JsonNode expected) {
		if (actual == null || expected == null || expected.isMissingNode() || expected.isNull()) {
			return TriState.UNKNOWN;
		}
		if (actual instanceof Number && expected.isNumber()) {
			try {
				return decimal(actual).compareTo(expected.decimalValue()) == 0 ? TriState.TRUE : TriState.FALSE;
			}
			catch (NumberFormatException e) {
				return TriState.UNKNOWN;
			}
		}
		if (actual instanceof Boolean && expected.isBoolean()) {
			return actual.equals(expected.asBoolean()) ? TriState.TRUE : TriState.FALSE;
		}
		if (actual instanceof String && expected.isTextual()) {
			return actual.equals(expected.asText()) ? TriState.TRUE : TriState.FALSE;
		}
		return TriState.UNKNOWN;
	}

	private TriState compareNumbers(Object actual, JsonNode expected, String operator) {
		if (!(actual instanceof Number) || expected == null || !expected.isNumber()) {
			return TriState.UNKNOWN;
		}
		try {
			int comparison = decimal(actual).compareTo(expected.decimalValue());
			boolean matches = "GREATER_THAN".equals(operator) ? comparison > 0
			        : "LESS_THAN".equals(operator) ? comparison < 0 : "GREATER_OR_EQUAL".equals(operator) ? comparison >= 0
			                : "LESS_OR_EQUAL".equals(operator) && comparison <= 0;
			return matches ? TriState.TRUE : TriState.FALSE;
		}
		catch (NumberFormatException e) {
			return TriState.UNKNOWN;
		}
	}

	private BigDecimal decimal(Object value) {
		return new BigDecimal(String.valueOf(value));
	}
}
