/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.dto;

import java.io.Serializable;

/**
 * Metadata projection of a {@link CareModel}: loaded with a column projection so the large
 * {@code configuration} envelope is never selected. Used by the membership listing path.
 */
public class CareModelRef implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private Integer careModelId;
	
	private String uuid;
	
	private String code;
	
	private String name;
	
	private String privilege;
	
	private String status;
	
	private Boolean retired;
	
	public Integer getCareModelId() {
		return careModelId;
	}
	
	public void setCareModelId(Integer careModelId) {
		this.careModelId = careModelId;
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	
	public String getCode() {
		return code;
	}
	
	public void setCode(String code) {
		this.code = code;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getPrivilege() {
		return privilege;
	}
	
	public void setPrivilege(String privilege) {
		this.privilege = privilege;
	}
	
	public String getStatus() {
		return status;
	}
	
	public void setStatus(String status) {
		this.status = status;
	}
	
	public Boolean getRetired() {
		return retired;
	}
	
	public void setRetired(Boolean retired) {
		this.retired = retired;
	}
}
