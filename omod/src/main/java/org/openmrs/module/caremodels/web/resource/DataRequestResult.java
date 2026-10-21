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

/**
 * Request/response delegate for {@link DataRequestResource}.
 */
public class DataRequestResult {
	
	// ---- request fields ----
	
	private String patient;
	
	private String model;
	
	private String tab;
	
	private String dataSource;
	
	// ---- response fields ----
	
	private String modelCode;
	
	private String memberUuid;
	
	/** Resolver output as a JSON document string. */
	private String resultJson;
	
	public String getPatient() {
		return patient;
	}
	
	public void setPatient(String patient) {
		this.patient = patient;
	}
	
	public String getModel() {
		return model;
	}
	
	public void setModel(String model) {
		this.model = model;
	}
	
	public String getTab() {
		return tab;
	}
	
	public void setTab(String tab) {
		this.tab = tab;
	}
	
	public String getDataSource() {
		return dataSource;
	}
	
	public void setDataSource(String dataSource) {
		this.dataSource = dataSource;
	}
	
	public String getModelCode() {
		return modelCode;
	}
	
	public void setModelCode(String modelCode) {
		this.modelCode = modelCode;
	}
	
	public String getMemberUuid() {
		return memberUuid;
	}
	
	public void setMemberUuid(String memberUuid) {
		this.memberUuid = memberUuid;
	}
	
	public String getResultJson() {
		return resultJson;
	}
	
	public void setResultJson(String resultJson) {
		this.resultJson = resultJson;
	}
}
