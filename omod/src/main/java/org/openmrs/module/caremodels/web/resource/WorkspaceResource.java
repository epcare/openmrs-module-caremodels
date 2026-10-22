/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.web.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.CareModelAccessService;
import org.openmrs.module.caremodels.api.CareModelAccessService.AuthorizedAccess;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

/**
 * Guarded workspace endpoint — serves the published definition of ONE selected inner tab of ONE
 * selected model for ONE patient, after full re-authorization on every request. GET
 * /ws/rest/v1/caremodels/workspace?patient={uuid}&amp;model={code|uuid}&amp;tab={tabId} The
 * response contains only the requested tab (id, label, widgets). The full definition never crosses
 * the wire; data-backed widgets carry the data source keys the client may subsequently request
 * through the data endpoint — which independently re-checks that each requested source is declared
 * in this same tab.
 */
@Resource(name = RestConstants.VERSION_1 + "/caremodels/workspace", supportedClass = WorkspaceTab.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class WorkspaceResource extends DelegatingCrudResource<WorkspaceTab> {
	
	@Override
	public WorkspaceTab newDelegate() {
		return new WorkspaceTab();
	}
	
	@Override
	public WorkspaceTab save(WorkspaceTab delegate) {
		throw new UnsupportedOperationException("Workspace definitions are authored in the designer");
	}
	
	@Override
	public WorkspaceTab getByUniqueId(String uniqueId) {
		throw new UnsupportedOperationException("Use ?patient=&model=&tab=");
	}
	
	/** RESTWS dispatches requests containing q/patient/model/tab filters through search(). */
	@Override
	protected PageableResult doSearch(RequestContext context) throws ResponseException {
		return doGetAll(context);
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		String patient = trimToNull(context.getParameter("patient"));
		String model = trimToNull(context.getParameter("model"));
		String tab = trimToNull(context.getParameter("tab"));
		if (patient == null || model == null) {
			throw new IllegalArgumentException(
			        "The patient and model parameters are required; tab is optional (default tab)");
		}
		
		AuthorizedAccess access = Context.getService(CareModelAccessService.class).authorizeWorkspace(patient, model, tab);
		
		WorkspaceTab result = new WorkspaceTab();
		result.setModelCode(access.getModelCode());
		result.setModelUuid(access.getModelUuid());
		result.setMemberUuid(access.getMemberUuid());
		result.setTabId(access.getTabDefinition().path("id").asText(null));
		result.setTabLabel(access.getTabDefinition().path("label").asText(null));
		
		List<JsonNode> widgets = new ArrayList<JsonNode>();
		for (JsonNode widget : access.getTabDefinition().path("widgets")) {
			widgets.add(widget);
		}
		result.setWidgets(widgets);
		
		JsonNode layouts = access.getTabDefinition().path("layouts");
		Iterator<Map.Entry<String, JsonNode>> layoutFields = layouts.fields();
		while (layoutFields.hasNext()) {
			Map.Entry<String, JsonNode> field = layoutFields.next();
			result.getLayouts().put(field.getKey(), field.getValue());
		}
		
		List<JsonNode> tabs = new ArrayList<JsonNode>();
		for (JsonNode summary : access.getTabs()) {
			tabs.add(summary);
		}
		result.setTabs(tabs);
		result.setDescriptors(new org.openmrs.module.caremodels.workspace.WorkspaceDescriptors().resolve(access));
		return new NeedsPaging<WorkspaceTab>(Collections.singletonList(result), context);
	}
	
	@Override
	protected void delete(WorkspaceTab delegate, String reason, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Delete is not supported for workspace");
	}
	
	@Override
	public void purge(WorkspaceTab delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Purge is not supported for workspace");
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("modelCode");
		description.addProperty("modelUuid");
		description.addProperty("memberUuid");
		description.addProperty("tabId");
		description.addProperty("tabLabel");
		if (representation instanceof DefaultRepresentation) {
			description.addProperty("widgets");
			description.addProperty("layouts");
			description.addProperty("tabs");
			description.addProperty("descriptors");
		}
		return description;
	}
	
	@Override
	public String getResourceVersion() {
		return "1.0";
	}
	
	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
