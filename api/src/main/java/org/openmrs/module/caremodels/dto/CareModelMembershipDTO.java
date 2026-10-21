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
import java.util.Date;

/**
 * Safe membership metadata returned by the patient membership endpoint. Deliberately carries no
 * definition JSON, no membership_data provenance and no clinical data: the membership listing must
 * never leak model configuration or clinical content.
 */
public class CareModelMembershipDTO implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private String memberUuid;
	
	private String modelUuid;
	
	private String modelCode;
	
	private String modelName;
	
	/** Patient-friendly short label from the published definition's metadata.display.label. */
	private String displayLabel;
	
	private Integer displayOrder;
	
	private Integer episodeNumber;
	
	private Date dateEnrolled;
	
	public String getMemberUuid() {
		return memberUuid;
	}
	
	public void setMemberUuid(String memberUuid) {
		this.memberUuid = memberUuid;
	}
	
	public String getModelUuid() {
		return modelUuid;
	}
	
	public void setModelUuid(String modelUuid) {
		this.modelUuid = modelUuid;
	}
	
	public String getModelCode() {
		return modelCode;
	}
	
	public void setModelCode(String modelCode) {
		this.modelCode = modelCode;
	}
	
	public String getModelName() {
		return modelName;
	}
	
	public void setModelName(String modelName) {
		this.modelName = modelName;
	}
	
	public String getDisplayLabel() {
		return displayLabel;
	}
	
	public void setDisplayLabel(String displayLabel) {
		this.displayLabel = displayLabel;
	}
	
	public Integer getDisplayOrder() {
		return displayOrder;
	}
	
	public void setDisplayOrder(Integer displayOrder) {
		this.displayOrder = displayOrder;
	}
	
	public Integer getEpisodeNumber() {
		return episodeNumber;
	}
	
	public void setEpisodeNumber(Integer episodeNumber) {
		this.episodeNumber = episodeNumber;
	}
	
	public Date getDateEnrolled() {
		return dateEnrolled;
	}
	
	public void setDateEnrolled(Date dateEnrolled) {
		this.dateEnrolled = dateEnrolled;
	}
}
