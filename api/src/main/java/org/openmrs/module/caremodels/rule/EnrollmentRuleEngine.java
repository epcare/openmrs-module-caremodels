/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** Evaluates enrollment rules and selects one deterministic, safety-first outcome. */
public class EnrollmentRuleEngine {

	public enum Action {
		EXCLUDE(3), PROPOSE_CONFIRMATION(2), AUTO_ENROLL(1);

		private final int priority;

		Action(int priority) {
			this.priority = priority;
		}
	}

	public static final class RuleEvaluation {

		private final String ruleId;

		private final Action action;

		private final TriState result;

		private RuleEvaluation(String ruleId, Action action, TriState result) {
			this.ruleId = ruleId;
			this.action = action;
			this.result = result;
		}

		public String getRuleId() {
			return ruleId;
		}

		public Action getAction() {
			return action;
		}

		public TriState getResult() {
			return result;
		}
	}

	public static final class Decision {

		private final RuleEvaluation selected;

		private final List<RuleEvaluation> evaluations;

		private Decision(RuleEvaluation selected, List<RuleEvaluation> evaluations) {
			this.selected = selected;
			this.evaluations = Collections.unmodifiableList(new ArrayList<RuleEvaluation>(evaluations));
		}

		public boolean hasMatch() {
			return selected != null;
		}

		public RuleEvaluation getSelected() {
			return selected;
		}

		public List<RuleEvaluation> getEvaluations() {
			return evaluations;
		}
	}

	private final RuleExpressionEvaluator expressionEvaluator;

	public EnrollmentRuleEngine() {
		this(new RuleExpressionEvaluator());
	}

	public EnrollmentRuleEngine(RuleExpressionEvaluator expressionEvaluator) {
		this.expressionEvaluator = expressionEvaluator;
	}

	public Decision evaluate(JsonNode rules, RuleEvidenceProvider evidenceProvider, Date asOf) {
		List<RuleEvaluation> evaluations = new ArrayList<RuleEvaluation>();
		RuleEvaluation selected = null;
		if (rules != null && rules.isArray()) {
			for (JsonNode rule : rules) {
				Action action = parseAction(rule.path("action").asText(null));
				TriState result = action == null ? TriState.UNKNOWN : expressionEvaluator.evaluate(rule.path("expression"),
				    evidenceProvider, asOf);
				RuleEvaluation evaluation = new RuleEvaluation(rule.path("id").asText(null), action, result);
				evaluations.add(evaluation);
				if (result == TriState.TRUE && isPreferred(evaluation, selected)) {
					selected = evaluation;
				}
			}
		}
		return new Decision(selected, evaluations);
	}

	private Action parseAction(String action) {
		try {
			return action == null ? null : Action.valueOf(action);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	private boolean isPreferred(RuleEvaluation candidate, RuleEvaluation current) {
		if (current == null || candidate.getAction().priority > current.getAction().priority) {
			return true;
		}
		if (candidate.getAction().priority < current.getAction().priority) {
			return false;
		}
		String candidateId = candidate.getRuleId() == null ? "" : candidate.getRuleId();
		String currentId = current.getRuleId() == null ? "" : current.getRuleId();
		return candidateId.compareTo(currentId) < 0;
	}
}
