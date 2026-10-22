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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Response delegate for {@link WorkspaceResource}: the authorized view of ONE selected inner tab.
 */
public class WorkspaceTab {
	
	private JsonNode descriptors;
	
	public JsonNode getDescriptors() {
		return descriptors;
	}
	
	public void setDescriptors(JsonNode descriptors) {
		this.descriptors = descriptors;
	}
	
	private String modelCode;
	
	private String modelUuid;
	
	private String memberUuid;
	
	private String tabId;
	
	private String tabLabel;
	
	/** Widget instances of the tab, exactly as authored in the published definition. */
	private List<JsonNode> widgets = new ArrayList<JsonNode>();
	
	/** Responsive layout variants (wide/narrow) when authored. */
	private Map<String, JsonNode> layouts = new LinkedHashMap<String, JsonNode>();
	
	/** Id + label of every tab in the definition, for the inner-tab strip. */
	private List<JsonNode> tabs = new ArrayList<JsonNode>();
	
	public String getModelCode() {
		return modelCode;
	}
	
	public void setModelCode(String modelCode) {
		this.modelCode = modelCode;
	}
	
	public String getModelUuid() {
		return modelUuid;
	}
	
	public void setModelUuid(String modelUuid) {
		this.modelUuid = modelUuid;
	}
	
	public String getMemberUuid() {
		return memberUuid;
	}
	
	public void setMemberUuid(String memberUuid) {
		this.memberUuid = memberUuid;
	}
	
	public String getTabId() {
		return tabId;
	}
	
	public void setTabId(String tabId) {
		this.tabId = tabId;
	}
	
	public String getTabLabel() {
		return tabLabel;
	}
	
	public void setTabLabel(String tabLabel) {
		this.tabLabel = tabLabel;
	}
	
	public List<JsonNode> getWidgets() {
		return widgets;
	}
	
	public void setWidgets(List<JsonNode> widgets) {
		this.widgets = widgets;
	}
	
	public Map<String, JsonNode> getLayouts() {
		return layouts;
	}
	
	public void setLayouts(Map<String, JsonNode> layouts) {
		this.layouts = layouts;
	}
	
	public List<JsonNode> getTabs() {
		return tabs;
	}
	
	public void setTabs(List<JsonNode> tabs) {
		this.tabs = tabs;
	}
}
