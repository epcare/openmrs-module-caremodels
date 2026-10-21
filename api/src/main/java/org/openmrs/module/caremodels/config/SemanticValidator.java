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

import java.util.Iterator;
import java.util.Map;

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

	public SemanticValidator(OpenmrsReferenceChecker checker) {
		this.checker = checker;
	}

	public ValidationResult validateDefinition(JsonNode definition) {
		ValidationResult result = ValidationResult.valid();
		scanForBannedKeysAndUrls(definition, "$", result);

		JsonNode terminology = definition.path("terminology");
		JsonNode dataSources = definition.path("dataSources");
		JsonNode forms = definition.path("forms");
		JsonNode actions = definition.path("actions");
		JsonNode workspace = definition.path("workspace");

		validateTerminology(terminology, result);
		validateDataSources(dataSources, terminology, result);
		validateForms(forms, terminology, result);
		validateActions(actions, forms, result);
		validateWorkspace(workspace, dataSources, actions, forms, terminology, result);
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
			result.merge(org.openmrs.module.caremodels.resolver.SourceContract.validate(dataSource,
			    new org.openmrs.module.caremodels.resolver.DataResolverRegistry(), path));

			if (Registry.DATA_SOURCE_TYPES.contains(type) && !Registry.DS_REGISTERED.equals(type)) {
				checkAliasExists(dataSource, "conceptAlias", "concepts", terminology, path, result);
				checkAliasExists(dataSource, "programAlias", "programs", terminology, path, result);
				checkAliasExists(dataSource, "encounterTypeAlias", "encounterTypes", terminology, path, result);
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
		for (int i = 0; i < forms.size(); i++) {
			String path = "$.forms[" + i + "]";
			JsonNode form = forms.get(i);
			String alias = form.path("alias").asText();
			if (!terminology.path("forms").has(alias)) {
				result.addError(path + ".alias", "Form alias '" + alias + "' is not declared in terminology.forms");
			}
			checkAliasExists(form, "encounterTypeAlias", "encounterTypes", terminology, path, result);
			if (!form.path("formUuid").asText().equals(terminology.path("forms").path(alias).asText())) {
				result.addError(path + ".formUuid", "Form UUID must match terminology.forms alias");
			}

		}
	}

	private void validateActions(JsonNode actions, JsonNode forms, ValidationResult result) {
		if (!actions.isArray()) {
			return;
		}
		for (int i = 0; i < actions.size(); i++) {
			String path = "$.actions[" + i + "]";
			JsonNode action = actions.get(i);
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

		for (int t = 0; t < tabs.size(); t++) {
			JsonNode tab = tabs.get(t);
			String tabPath = "$.workspace.tabs[" + t + "]";
			JsonNode widgets = tab.path("widgets");
			for (int w = 0; w < widgets.size(); w++) {
				validateWidget(widgets.get(w), tabPath + ".widgets[" + w + "]", dataSources, actions, forms, terminology,
				    result);
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
		if (Registry.WIDGET_METRIC.equals(type) && !observation)
			result.addError(path + ".dataSource", "Metric requires observation values");
		if (Registry.WIDGET_TREND_CHART.equals(type)
		        && (!observation || !("NUMBER".equals(resultType) || "TIMESERIES".equals(resultType))))
			result.addError(path + ".dataSource", "Chart requires declared numeric observation sources");
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
