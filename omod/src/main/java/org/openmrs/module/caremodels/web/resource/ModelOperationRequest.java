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
 * Request/response delegate for {@link ModelOperationResource}. Field names are the request body
 * properties and the response representation.
 */
public class ModelOperationRequest {
	
	private String operation;
	
	private String code;
	
	private String name;
	
	private String description;
	
	private String privilege;
	
	/** JSON definition (draft) as a string for create/saveDraft/validate. */
	private String definition;
	
	/** Storage envelope JSON for import; export payload on envelope/published responses. */
	private String envelope;
	
	private Integer expectedRevision;
	
	private Integer currentRevision;
	
	private String version;
	
	private String sourceCode;
	
	private String newCode;
	
	private String newName;
	
	// ---- response fields ----
	
	private String modelUuid;
	
	private String modelCode;
	
	private String status;
	
	private Integer revision;
	
	private Boolean valid;
	
	private String validationErrors;
	
	private Boolean conflict;
	
	public String getOperation() {
		return operation;
	}
	
	public void setOperation(String operation) {
		this.operation = operation;
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
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public String getPrivilege() {
		return privilege;
	}
	
	public void setPrivilege(String privilege) {
		this.privilege = privilege;
	}
	
	public String getDefinition() {
		return definition;
	}
	
	public void setDefinition(String definition) {
		this.definition = definition;
	}
	
	public String getEnvelope() {
		return envelope;
	}
	
	public void setEnvelope(String envelope) {
		this.envelope = envelope;
	}
	
	public Integer getExpectedRevision() {
		return expectedRevision;
	}
	
	public void setExpectedRevision(Integer expectedRevision) {
		this.expectedRevision = expectedRevision;
	}
	
	public Integer getCurrentRevision() {
		return currentRevision;
	}
	
	public void setCurrentRevision(Integer currentRevision) {
		this.currentRevision = currentRevision;
	}
	
	public String getVersion() {
		return version;
	}
	
	public void setVersion(String version) {
		this.version = version;
	}
	
	public String getSourceCode() {
		return sourceCode;
	}
	
	public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}
	
	public String getNewCode() {
		return newCode;
	}
	
	public void setNewCode(String newCode) {
		this.newCode = newCode;
	}
	
	public String getNewName() {
		return newName;
	}
	
	public void setNewName(String newName) {
		this.newName = newName;
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
	
	public String getStatus() {
		return status;
	}
	
	public void setStatus(String status) {
		this.status = status;
	}
	
	public Integer getRevision() {
		return revision;
	}
	
	public void setRevision(Integer revision) {
		this.revision = revision;
	}
	
	public Boolean getValid() {
		return valid;
	}
	
	public void setValid(Boolean valid) {
		this.valid = valid;
	}
	
	public String getValidationErrors() {
		return validationErrors;
	}
	
	public void setValidationErrors(String validationErrors) {
		this.validationErrors = validationErrors;
	}
	
	public Boolean getConflict() {
		return conflict;
	}
	
	public void setConflict(Boolean conflict) {
		this.conflict = conflict;
	}
}
