/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.config;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Semantic validation of a structurally valid Care Model definition. Checks what JSON Schema
 * cannot: that terminology aliases resolve to real, non-retired OpenMRS objects of the correct
 * referent type; that widgets reference declared data sources, registered types, existing forms and
 * declared actions; that rule aliases are declared; and that no banned key (privilege claims,
 * facility overlays, executable code markers) or arbitrary URL made it into the document.
 * <p>
 * Operates on the parsed definition tree; run after {@link JsonSchemaValidator}, never instead of
 * it.
 */
public class SemanticValidator {
	
	/**
	 * Keys that must never appear anywhere in a definition. Privileges live in
	 * care_model.privilege; facility activation is out of scope by design; scripts/sql/css are
	 * untrusted executables.
	 */
	static final String[] BANNED_KEYS = { "privilege", "privileges", "facility", "facilities", "overrides", "override",
	        "script", "javascript", "css", "sql", "query", "expression_url", "import_url" };
	
	private final OpenmrsReferenceChecker checker;
	
	private final org.openmrs.module.caremodels.resolver.DataResolverRegistry resolvers;
	
	public SemanticValidator(OpenmrsReferenceChecker checker) {
		this(checker, new org.openmrs.module.caremodels.resolver.DataResolverRegistry());
	}
	
	public SemanticValidator(OpenmrsReferenceChecker checker,
	    org.openmrs.module.caremodels.resolver.DataResolverRegistry registry) {
		this.checker = checker;
		this.resolvers = registry;
	}
	
	public ValidationResult validateDefinition(JsonNode definition) {
		ValidationResult result = ValidationResult.valid();
		scanForBannedKeysAndUrls(definition, "$", result);
		
		JsonNode terminology = definition.path("terminology");
		JsonNode dataSources = definition.path("dataSources");
		JsonNode forms = definition.path("forms");
		JsonNode actions = definition.path("actions");
		JsonNode workspace = definition.path("workspace");
		JsonNode enrollment = definition.path("enrollment");
		
		validateTerminology(terminology, result);
		validateDataSources(dataSources, terminology, result);
		validateForms(forms, terminology, result);
		validateActions(actions, forms, terminology, result);
		validateWorkspace(workspace, dataSources, actions, forms, terminology, result);
		validateEnrollment(enrollment, terminology, result);
		validatePathways(definition.path("pathways"), result);
		validateRuleExpressions(definition.path("pathways").path("transitions"), "expression", "$.pathways.transitions",
		    terminology, result);
		validateRuleExpressions(definition.path("automation").path("rules"), "expression", "$.automation.rules",
		    terminology, result);
		validateUniqueIds(definition.path("automation").path("rules"), "$.automation.rules", "automation rule", result);
		return result;
	}
	
	// ========================
	// Terminology
	// ========================
	
	private void validateTerminology(JsonNode terminology, ValidationResult result) {
		for (String kind : new String[] { "concepts", "programs", "encounterTypes", "forms", "codedAnswers" }) {
			JsonNode aliases = terminology.path(kind);
			Iterator<Map.Entry<String, JsonNode>> fields = aliases.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> field = fields.next();
				String uuid = field.getValue().asText();
				boolean exists;
				switch (kind) {
					case "concepts":
						exists = checker.conceptExists(uuid);
						break;
					case "programs":
						exists = checker.programExists(uuid);
						break;
					case "encounterTypes":
						exists = checker.encounterTypeExists(uuid);
						break;
					case "forms":
						exists = checker.formExists(uuid);
						break;
					default:
						exists = checker.conceptExists(uuid); // coded answers are concepts
						break;
				}
				if (!exists) {
					result.addError("$.terminology." + kind + "." + field.getKey(),
					    "Alias does not resolve to an existing non-retired OpenMRS " + singular(kind) + ": " + uuid);
				}
			}
		}
	}
	
	private String singular(String kind) {
		switch (kind) {
			case "concepts":
			case "codedAnswers":
				return "concept";
			case "programs":
				return "program";
			case "encounterTypes":
				return "encounter type";
			case "forms":
				return "form";
			default:
				return kind;
		}
	}
	
	// ========================
	// Data sources
	// ========================
	
	private void validateDataSources(JsonNode dataSources, JsonNode terminology, ValidationResult result) {
		Iterator<Map.Entry<String, JsonNode>> fields = dataSources.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> field = fields.next();
			String path = "$.dataSources." + field.getKey();
			JsonNode dataSource = field.getValue();
			String type = dataSource.path("type").asText();
			result.merge(org.openmrs.module.caremodels.resolver.SourceContract.validate(dataSource, resolvers, path));
			
			if (Registry.DATA_SOURCE_TYPES.contains(type)) {
				checkAliasExists(dataSource, "conceptAlias", "concepts", terminology, path, result);
				checkAliasExists(dataSource, "programAlias", "programs", terminology, path, result);
				checkAliasExists(dataSource, "encounterTypeAlias", "encounterTypes", terminology, path, result);
				checkAliasExists(dataSource, "formAlias", "forms", terminology, path, result);
			}
			
			// A grouped source must reference an actual concept set; an N/A concept that is not a
			// set can never qualify, regardless of its class.
			if (Registry.DS_OBSERVATION_SET.equals(type)
			        && !checker.conceptIsSet(terminology.path("concepts").path(dataSource.path("conceptAlias").asText())
			                .asText(null))) {
				result.addError(path + ".conceptAlias",
				    "Parent concept must be an existing non-retired concept set with at least one member");
			}
			
			// Windows and result caps must respect server bounds (schema bounds are the outer limits;
			// tighter server-side caps may apply per resolver).
			int windowDays = dataSource.path("windowDays").asInt(0);
			if (windowDays > 3650) {
				result.addError(path + ".windowDays", "Date window exceeds the 3650-day bound");
			}
		}
	}
	
	private void checkAliasExists(JsonNode node, String aliasField, String kind, JsonNode terminology, String path,
	        ValidationResult result) {
		JsonNode alias = node.path(aliasField);
		if (alias.isMissingNode()) {
			return;
		}
		String name = alias.asText();
		if (!terminology.path(kind).has(name)) {
			result.addError(path + "." + aliasField, "Alias '" + name + "' is not declared in terminology." + kind);
		}
	}
	
	// ========================
	// Forms and actions
	// ========================
	
	private void validateForms(JsonNode forms, JsonNode terminology, ValidationResult result) {
		if (!forms.isArray()) {
			return;
		}
		Set<String> aliases = new HashSet<String>();
		for (int i = 0; i < forms.size(); i++) {
			String path = "$.forms[" + i + "]";
			JsonNode form = forms.get(i);
			String alias = form.path("alias").asText();
			checkUniqueId(alias, path + ".alias", "form alias", aliases, result);
			if (!terminology.path("forms").has(alias)) {
				result.addError(path + ".alias", "Form alias '" + alias + "' is not declared in terminology.forms");
			}
			checkAliasExists(form, "encounterTypeAlias", "encounterTypes", terminology, path, result);
			if (!form.path("formUuid").asText().equals(terminology.path("forms").path(alias).asText())) {
				result.addError(path + ".formUuid", "Form UUID must match terminology.forms alias");
			}
			
		}
	}
	
	private void validateActions(JsonNode actions, JsonNode forms, JsonNode terminology, ValidationResult result) {
		if (!actions.isArray()) {
			return;
		}
		Set<String> ids = new HashSet<String>();
		for (int i = 0; i < actions.size(); i++) {
			String path = "$.actions[" + i + "]";
			JsonNode action = actions.get(i);
			checkUniqueId(action.path("id").asText(null), path + ".id", "action", ids, result);
			String type = action.path("type").asText();
			
			if (Registry.ACTION_LAUNCH_FORM.equals(type)) {
				String formAlias = action.path("formAlias").asText(null);
				if (formAlias != null && !hasFormAlias(forms, formAlias)) {
					result.addError(path + ".formAlias", "Action launches form alias '" + formAlias
					        + "' which is not declared in forms");
				}
			}
			
			if (Registry.ACTION_ENROLLMENT_TRANSITION.equals(type) && action.path("requiresReason").asBoolean(false)
			        && action.path("transition").asText("").isEmpty()) {
				result.addError(path + ".transition", "Enrollment transition action requires a transition target");
			}
			validateRuleExpression(action.path("availabilityRule"), path + ".availabilityRule", terminology, result);
		}
	}
	
	private boolean hasFormAlias(JsonNode forms, String alias) {
		for (int i = 0; i < forms.size(); i++) {
			if (alias.equals(forms.get(i).path("alias").asText(null))) {
				return true;
			}
		}
		return false;
	}
	
	// ========================
	// Workspace
	// ========================
	
	private void validateWorkspace(JsonNode workspace, JsonNode dataSources, JsonNode actions, JsonNode forms,
	        JsonNode terminology, ValidationResult result) {
		JsonNode tabs = workspace.path("tabs");
		if (!tabs.isArray()) {
			return;
		}
		String defaultTab = workspace.path("defaultTab").asText(null);
		if (defaultTab != null && !defaultTab.isEmpty()) {
			boolean found = false;
			for (int i = 0; i < tabs.size(); i++) {
				if (defaultTab.equals(tabs.get(i).path("id").asText(null))) {
					found = true;
					break;
				}
			}
			if (!found) {
				result.addError("$.workspace.defaultTab", "defaultTab '" + defaultTab
				        + "' does not match any declared tab id");
			}
		}
		
		Set<String> tabIds = new HashSet<String>();
		for (int t = 0; t < tabs.size(); t++) {
			JsonNode tab = tabs.get(t);
			String tabPath = "$.workspace.tabs[" + t + "]";
			checkUniqueId(tab.path("id").asText(null), tabPath + ".id", "tab", tabIds, result);
			validateRuleExpression(tab.path("visibilityRule"), tabPath + ".visibilityRule", terminology, result);
			JsonNode widgets = tab.path("widgets");
			Set<String> widgetIds = new HashSet<String>();
			for (int w = 0; w < widgets.size(); w++) {
				checkUniqueId(widgets.get(w).path("id").asText(null), tabPath + ".widgets[" + w + "].id", "widget",
				    widgetIds, result);
				validateWidget(widgets.get(w), tabPath + ".widgets[" + w + "]", dataSources, actions, forms, terminology,
				    result);
			}
			validateLayouts(tab.path("layouts"), tabPath + ".layouts", widgetIds, result);
		}
	}
	
	private void validateLayouts(JsonNode layouts, String path, Set<String> widgetIds, ValidationResult result) {
		for (String size : new String[] { "wide", "narrow" }) {
			JsonNode layout = layouts.path(size);
			Set<String> positioned = new HashSet<String>();
			for (int i = 0; i < layout.size(); i++) {
				JsonNode item = layout.get(i);
				String itemPath = path + "." + size + "[" + i + "]";
				String widgetId = item.path("widgetId").asText(null);
				if (!widgetIds.contains(widgetId)) {
					result.addError(itemPath + ".widgetId", "Layout references undeclared widget '" + widgetId + "'");
				} else if (!positioned.add(widgetId)) {
					result.addError(itemPath + ".widgetId", "Widget '" + widgetId + "' occurs more than once in the layout");
				}
				if (item.path("col").asInt() + item.path("w").asInt() - 1 > 12) {
					result.addError(itemPath, "Grid position extends beyond the 12-column layout");
				}
			}
		}
	}
	
	private void validateWidget(JsonNode widget, String path, JsonNode dataSources, JsonNode actions, JsonNode forms,
	        JsonNode terminology, ValidationResult result) {
		String type = widget.path("type").asText();
		if (!Registry.WIDGET_TYPES.contains(type)) {
			result.addError(path + ".type", "Widget type '" + type + "' is not registered");
		}

		// Data-backed widgets must declare a data source that exists; resolvers are only invoked
		// for sources declared in the selected visible tab, so an undeclared name can never resolve.
		String dataSource = widget.path("dataSource").asText(null);
		for (JsonNode binding : widget.path("dataSources")) {
			if (!dataSources.has(binding.asText())) {
				result.addError(path + ".dataSources", "Undeclared source: " + binding.asText());
			}
		}
		if (widget.has("dataSources") && !Registry.WIDGET_TREND_CHART.equals(type)) {
			result.addError(path + ".dataSources", "Multiple bindings are supported only by trend-chart");
		}
		for (JsonNode alias : widget.path("formAliases")) {
			if (!terminology.path("forms").has(alias.asText()) || !hasFormAlias(forms, alias.asText())) {
				result.addError(path + ".formAliases", "Undeclared form alias: " + alias.asText());
			}
		}
		JsonNode position = widget.path("position");
		if (position.has("col") && position.path("col").asInt() + position.path("w").asInt() > 13) {
			result.addError(path + ".position", "Widget exceeds the 12-column grid");
		}
		if (dataSource != null && !dataSources.has(dataSource)) {
			result.addError(path + ".dataSource", "Widget references data source '" + dataSource
			        + "' which is not declared in dataSources");
		}

        if ((Registry.WIDGET_FORM_LAUNCHER.equals(type) || Registry.WIDGET_ACTION_PANEL.equals(type)) && (widget.has("dataSource") || widget.has("dataSources")))
            result.addError(path, "Action widgets cannot bind clinical sources");
        JsonNode properties = widget.path("properties");
        if (properties.has("targetMin") && properties.has("targetMax") && properties.path("targetMin").asDouble() > properties.path("targetMax").asDouble())
            result.addError(path + ".properties", "targetMin exceeds targetMax");
        java.util.Set<String> mappings = new java.util.HashSet<>();
        for (JsonNode mapping : properties.path("statusMappings")) if (!mappings.add(mapping.path("value").asText()))
            result.addError(path + ".properties.statusMappings", "Duplicate status value");
        java.util.Set<String> columns = new java.util.HashSet<>();
        for (JsonNode column : properties.path("columns")) if (!columns.add(column.path("field").asText()))
            result.addError(path + ".properties.columns", "Duplicate field");
        if (widget.has("dataSources") && widget.has("dataSource")) result.addError(path, "Use dataSource or dataSources, not both");
        if (widget.has("dataSources")) {
            for (JsonNode key : widget.path("dataSources")) validateBinding(type, dataSources.path(key.asText()), path, result);
        } else if (dataSource != null) validateBinding(type, dataSources.path(dataSource), path, result);

		JsonNode actionIds = widget.path("actionIds");
		if (actionIds.isArray()) {
			for (int i = 0; i < actionIds.size(); i++) {
				String actionId = actionIds.get(i).asText();
				if (!hasActionId(actions, actionId)) {
					result.addError(path + ".actionIds[" + i + "]", "Widget references action '" + actionId
					        + "' which is not declared in actions");
				}
			}
		}

		String formAlias = widget.path("formAlias").asText(null);
		if (formAlias != null && !terminology.path("forms").has(formAlias)) {
			result.addError(path + ".formAlias", "Widget form alias '" + formAlias
			        + "' is not declared in terminology.forms");
		}
	}
	
	private void validateBinding(String type, JsonNode source, String path, ValidationResult result) {
		String id = org.openmrs.module.caremodels.resolver.SourceContract.resolverId(source);
		String resultType = source.path("resultType").asText();
		boolean observation = Registry.DS_LATEST_OBSERVATION.equals(id) || Registry.DS_HISTORICAL_OBSERVATIONS.equals(id);
		if (Registry.WIDGET_METRIC.equals(type)
		        && (!observation || "TABLE".equals(resultType) || "TIMESERIES".equals(resultType)))
			result.addError(path + ".dataSource", "Metric requires observation values");
		if (Registry.WIDGET_TREND_CHART.equals(type)
		        && (!observation || !("NUMBER".equals(resultType) || "TIMESERIES".equals(resultType))))
			result.addError(path + ".dataSource", "Chart requires declared numeric observation sources");
		if ((Registry.WIDGET_LATEST_SET_RESULTS.equals(type) || Registry.WIDGET_SET_HISTORY_TRENDS.equals(type))
		        && !Registry.DS_OBSERVATION_SET.equals(id))
			result.addError(path + ".dataSource", "Set widgets require a grouped observation-set source");
		if (Registry.WIDGET_NOTES_LIST.equals(type) && (!observation || !"STRING".equals(resultType)))
			result.addError(path + ".dataSource", "Notes require a text observation source");
		String required = null;
		if ("condition-list".equals(type) && !"conditions".equals(id) && !"encounter-diagnoses".equals(id))
			required = "conditions or encounter-diagnoses";
		if ("medication-summary".equals(type) && !"order-query".equals(id))
			required = "order-query";
		if ("program-summary".equals(type) && !"program-state".equals(id))
			required = "program-state";
		if ("patient-summary".equals(type) && !"patient-summary".equals(id))
			required = "patient-summary";
		if (required != null)
			result.addError(path + ".dataSource", "Required resolver: " + required);
	}
	
	private boolean hasActionId(JsonNode actions, String actionId) {
		for (int i = 0; i < actions.size(); i++) {
			if (actionId.equals(actions.get(i).path("id").asText(null))) {
				return true;
			}
		}
		return false;
	}
	
	private void validateEnrollment(JsonNode enrollment, JsonNode terminology, ValidationResult result) {
		JsonNode triggers = enrollment.path("triggers");
		for (int i = 0; i < triggers.size(); i++) {
			JsonNode trigger = triggers.get(i);
			String path = "$.enrollment.triggers[" + i + "]";
			checkAliasExists(trigger, "conceptAlias", "concepts", terminology, path, result);
			checkAliasExists(trigger, "encounterTypeAlias", "encounterTypes", terminology, path, result);
			checkAliasExists(trigger, "programAlias", "programs", terminology, path, result);
			String type = trigger.path("type").asText();
			String kind = type.startsWith("PROGRAM_") ? "programs" : type.startsWith("ENCOUNTER_") ? "encounterTypes" : type
			        .startsWith("OBSERVATION_") ? "concepts" : null;
			if (kind != null) {
				checkAliasExists(trigger, "alias", kind, terminology, path, result);
			}
		}
		validateUniqueIds(enrollment.path("rules"), "$.enrollment.rules", "enrollment rule", result);
		validateRuleExpressions(enrollment.path("rules"), "expression", "$.enrollment.rules", terminology, result);
	}
	
	private void validatePathways(JsonNode pathways, ValidationResult result) {
		Set<String> stages = validateUniqueIds(pathways.path("stages"), "$.pathways.stages", "pathway stage", result);
		JsonNode transitions = pathways.path("transitions");
		for (int i = 0; i < transitions.size(); i++) {
			for (String endpoint : new String[] { "from", "to" }) {
				String stage = transitions.get(i).path(endpoint).asText(null);
				if (!stages.contains(stage)) {
					result.addError("$.pathways.transitions[" + i + "]." + endpoint,
					    "Transition references undeclared pathway stage '" + stage + "'");
				}
			}
		}
	}
	
	private Set<String> validateUniqueIds(JsonNode items, String path, String kind, ValidationResult result) {
		Set<String> ids = new HashSet<String>();
		for (int i = 0; i < items.size(); i++) {
			checkUniqueId(items.get(i).path("id").asText(null), path + "[" + i + "].id", kind, ids, result);
		}
		return ids;
	}
	
	private void validateRuleExpressions(JsonNode owners, String field, String path, JsonNode terminology,
	        ValidationResult result) {
		for (int i = 0; i < owners.size(); i++) {
			validateRuleExpression(owners.get(i).path(field), path + "[" + i + "]." + field, terminology, result);
		}
	}
	
	private void validateRuleExpression(JsonNode expression, String path, JsonNode terminology, ValidationResult result) {
		if (expression == null || expression.isMissingNode()) {
			return;
		}
		for (String operator : new String[] { "all", "any" }) {
			JsonNode children = expression.path(operator);
			for (int i = 0; i < children.size(); i++) {
				validateRuleExpression(children.get(i), path + "." + operator + "[" + i + "]", terminology, result);
			}
		}
		if (expression.has("not")) {
			validateRuleExpression(expression.path("not"), path + ".not", terminology, result);
		}
		String type = expression.path("type").asText(null);
		String kind = "program".equals(type) ? "programs" : "encounter".equals(type) ? "encounterTypes" : "observation"
		        .equals(type) ? "concepts" : null;
		if (kind != null) {
			checkAliasExists(expression, "alias", kind, terminology, path, result);
		}
		checkAliasExists(expression, "codedAnswerAlias", "codedAnswers", terminology, path, result);
	}
	
	private void checkUniqueId(String id, String path, String kind, Set<String> seen, ValidationResult result) {
		if (id != null && !seen.add(id)) {
			result.addError(path, "Duplicate " + kind + " id '" + id + "'");
		}
	}
	
	// ========================
	// Banned content
	// ========================
	
	/**
	 * Depth-first scan for banned keys and arbitrary URL values anywhere in the document. Belt and
	 * braces over the schema's additionalProperties:false: a policy guarantee must not depend on a
	 * single mechanism.
	 */
	private void scanForBannedKeysAndUrls(JsonNode node, String path, ValidationResult result) {
		if (node.isObject()) {
			Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> field = fields.next();
				String key = field.getKey().toLowerCase();
				for (String banned : BANNED_KEYS) {
					if (key.equals(banned)) {
						result.addError(path + "." + field.getKey(), "Banned key '" + field.getKey()
						        + "' in care model definition");
						break;
					}
				}
				scanForBannedKeysAndUrls(field.getValue(), path + "." + field.getKey(), result);
			}
		} else if (node.isArray()) {
			for (int i = 0; i < node.size(); i++) {
				scanForBannedKeysAndUrls(node.get(i), path + "[" + i + "]", result);
			}
		} else if (node.isTextual()) {
			String value = node.asText();
			String lower = value.toLowerCase();
			if ((lower.startsWith("http://") || lower.startsWith("https://"))
			        && !value.equals(JsonSchemaValidator.SCHEMA_BASE_URI)) {
				result.addError(path, "Arbitrary URL values are not allowed in care model definitions");
			}
		}
	}
}
