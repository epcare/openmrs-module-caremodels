/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.workspace;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.openmrs.Form;
import org.openmrs.FormResource;
import org.openmrs.api.context.Context;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.caremodels.api.CareModelAccessService.AuthorizedAccess;
import org.openmrs.module.caremodels.resolver.ResolverSupport;

/** Safe descriptors for references actually used by the selected, authorized tab. */
public class WorkspaceDescriptors {

	public ObjectNode resolve(AuthorizedAccess access) {
        JsonNode definition = access.getPublishedDefinition();
        Set<String> formAliases = new LinkedHashSet<>();
        Set<String> actionIds = new LinkedHashSet<>();
        for (JsonNode widget : access.getTabDefinition().path("widgets")) {
            if (widget.hasNonNull("formAlias")) formAliases.add(widget.path("formAlias").asText());
            for (JsonNode alias : widget.path("formAliases")) formAliases.add(alias.asText());
            for (JsonNode id : widget.path("actionIds")) actionIds.add(id.asText());
        }
        Map<String, JsonNode> declaredActions = new LinkedHashMap<>();
        for (JsonNode action : definition.path("actions")) if (actionIds.contains(action.path("id").asText())) {
            declaredActions.put(action.path("id").asText(), action);
            if ("launch-form".equals(action.path("type").asText())) formAliases.add(action.path("formAlias").asText());
        }
        ObjectNode result = ResolverSupport.MAPPER.createObjectNode().put("version", "1.0.0");
        if (access.getMemberPathway() != null) result.set("pathway", access.getMemberPathway());
        ArrayNode forms = result.putArray("forms");
        Map<String, ObjectNode> formByAlias = new LinkedHashMap<>();
        for (String alias : formAliases) {
            ObjectNode descriptor = resolveForm(definition, alias);
            forms.add(descriptor); formByAlias.put(alias, descriptor);
        }
        ArrayNode actions = result.putArray("actions");
        for (String id : actionIds) {
            JsonNode action = declaredActions.get(id);
            ObjectNode descriptor = actions.addObject().put("id", id).put("available", false);
            if (action == null) { descriptor.put("reason", "undeclared-action"); continue; }
            String type = action.path("type").asText();
            descriptor.put("type", type).put("label", action.path("label").asText()).put("handlerVersion", "1.0.0");
            // No raw target, URL, arbitrary parameters or executable configuration crosses the wire.
            switch (type) {
                case "launch-form":
                    String alias = action.path("formAlias").asText();
                    descriptor.put("formAlias", alias).put("reason", formByAlias.get(alias).path("reason").asText()); break;
                case "open-order-entry": descriptor.put("reason", "registered-order-workspace-required"); break;
                case "open-appointments": descriptor.put("reason", ModuleFactory.getStartedModuleById("appointmentscheduling") == null
                    ? "appointments-module-unavailable" : "registered-appointment-workspace-required"); break;
                case "navigate-internal": descriptor.put("reason", "registered-authorized-destination-required"); break;
                case "enrollment-transition": descriptor.put("reason", "no-authorized-transition-rest-contract"); break;
                default: descriptor.put("reason", "unregistered-handler");
            }
            if (action.has("availabilityRule")) descriptor.put("reason", "registered-availability-rule-required");
        }
        return result;
    }

	private ObjectNode resolveForm(JsonNode definition, String alias) {
		ObjectNode descriptor = ResolverSupport.MAPPER.createObjectNode().put("alias", alias).put("available", false);
		JsonNode declared = null;
		for (JsonNode form : definition.path("forms"))
			if (alias.equals(form.path("alias").asText())) {
				declared = form;
				break;
			}
		String uuid = definition.path("terminology").path("forms").path(alias).asText(null);
		if (declared == null || uuid == null || !uuid.equals(declared.path("formUuid").asText()))
			return descriptor.put("reason", "undeclared-form");
		if (!Context.hasPrivilege("Get Forms") && !Context.hasPrivilege("View Forms")
		        && !Context.hasPrivilege("Manage Forms"))
			return descriptor.put("reason", "form-read-not-authorized");
		Form form = Context.getFormService().getFormByUuid(uuid);
		if (form == null)
			return descriptor.put("reason", "form-not-found");
		descriptor.put("formUuid", form.getUuid()).put("name", form.getName()).put("version", form.getVersion());
		if (form.isRetired())
			return descriptor.put("reason", "form-retired");
		if (!Boolean.TRUE.equals(form.getPublished()))
			return descriptor.put("reason", "form-unpublished");
		if (form.getEncounterType() == null || form.getEncounterType().isRetired())
			return descriptor.put("reason", "encounter-type-unavailable");
		String expected = declared.path("encounterTypeAlias").asText(null);
		if (expected != null
		        && !form.getEncounterType().getUuid()
		                .equals(definition.path("terminology").path("encounterTypes").path(expected).asText()))
			return descriptor.put("reason", "encounter-type-mismatch");
		descriptor.put("encounterTypeUuid", form.getEncounterType().getUuid()).put("encounterTypeName",
		    form.getEncounterType().getName());
		if (!Context.hasPrivilege("Add Encounters"))
			return descriptor.put("reason", "encounter-create-not-authorized");
		ArrayNode resources = descriptor.putArray("resourceNames");
		for (FormResource resource : Context.getFormService().getFormResourcesForForm(form)) {
			// Metadata only. Never evaluate custom datatypes or return raw resource references/content.
			if (resource.getName() != null && resources.size() < 20)
				resources.add(resource.getName());
		}
		return descriptor.put("reason", resources.size() == 0 ? "form-engine-resource-unavailable"
		        : "frontend-form-engine-not-verified");
	}
}
