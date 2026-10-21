/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.api;

import java.util.List;

import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.caremodels.config.StaleRevisionException;
import org.openmrs.module.caremodels.config.ValidationResult;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;
import org.openmrs.module.caremodels.security.CareModelsPrivileges;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for Care Model registry management: CRUD, lifecycle transitions and the versioned JSON
 * envelope. Administrative operations require {@link CareModelsPrivileges#MANAGE_CARE_MODELS};
 * publication additionally requires {@link CareModelsPrivileges#PUBLISH_CARE_MODELS}.
 */
public interface CareModelService extends OpenmrsService {
	
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel getCareModel(Integer careModelId) throws APIException;
	
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel getCareModelByUuid(String uuid) throws APIException;
	
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel getCareModelByCode(String code) throws APIException;
	
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	List<CareModel> getCareModels(String q, CareModelStatus status, boolean includeRetired, Integer startIndex, Integer limit)
	        throws APIException;
	
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	long getCareModelsCount(String q, CareModelStatus status, boolean includeRetired) throws APIException;
	
	/**
	 * Saves a model. The JSON envelope's draft is written with an optimistic compare-and-swap on
	 * {@link CareModel#getRevision()}; stale writes are rejected.
	 */
	@Transactional
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel saveCareModel(CareModel careModel) throws APIException;
	
	@Transactional
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	void purgeCareModel(CareModel careModel) throws APIException;
	
	/**
	 * Marks the model retired with a reason; retired models are excluded from patient tabs.
	 */
	@Transactional
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel retireCareModel(CareModel careModel, String reason) throws APIException;
	
	@Transactional
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel unretireCareModel(CareModel careModel) throws APIException;
	
	// ========================
	// Lifecycle: draft, validate, publish, duplicate, import/export (W2)
	// ========================
	
	/**
	 * Creates a model with an initial draft. The definition must be structurally and semantically
	 * valid; a blank or unregistered privilege fails closed.
	 */
	@Transactional
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel createCareModel(String code, String name, String description, String privilege, String definitionJson)
	        throws APIException;
	
	/**
	 * Saves a new draft with optimistic compare-and-swap on the envelope revision; stale writes
	 * throw {@link StaleRevisionException}. The definition must be valid before it is stored.
	 */
	@Transactional
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel saveDraft(String code, String definitionJson, Integer expectedRevision) throws APIException;
	
	/**
	 * Structural and semantic validation without persisting; surfaced by the designer's Validate
	 * action and by the validate REST endpoint.
	 */
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	ValidationResult validateDraft(String definitionJson) throws APIException;
	
	/**
	 * Publishes the current draft as a new version: full revalidation, MOD-007 privilege
	 * registration check, canonical checksum, archives the previous published snapshot, bumps the
	 * revision and sets status PUBLISHED. Requires both manage and publish privileges (checked
	 * inside the method because {@code @Authorized} is any-of, not all-of).
	 */
	@Transactional
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel publishCareModel(String code, String version, Integer expectedRevision) throws APIException;
	
	/** The current published definition JSON, or null when the model has never been published. */
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	String getPublishedDefinition(String code) throws APIException;
	
	/** Full storage envelope JSON (draft, published, bounded history); export payload. */
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	String getEnvelope(String code) throws APIException;
	
	/**
	 * Creates a new DRAFT model whose initial draft is the source model's current published
	 * definition. Privileges are never copied: the duplicate names its own required privilege.
	 */
	@Transactional
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel duplicateFromPublished(String sourceCode, String newCode, String newName, String privilege)
	        throws APIException;
	
	/**
	 * Imports a storage envelope JSON as a local model copy. Both the envelope structure and every
	 * embedded definition are validated; referenced OpenMRS resources must resolve locally. Status
	 * is PUBLISHED when the envelope carries a published snapshot, DRAFT otherwise.
	 */
	@Transactional
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel importModel(String code, String name, String privilege, String envelopeJson) throws APIException;
	
	/** PUBLISHED → INACTIVE; an inactive model never appears on patient tabs. */
	@Transactional
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel deactivateCareModel(String code) throws APIException;
	
	/** INACTIVE → PUBLISHED; requires an existing published snapshot. */
	@Transactional
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	CareModel reactivateCareModel(String code) throws APIException;
}
