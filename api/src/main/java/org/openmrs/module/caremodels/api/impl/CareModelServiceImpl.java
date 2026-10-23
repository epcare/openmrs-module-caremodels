/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.api.impl;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.caremodels.api.CareModelService;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.config.JsonSchemaValidator;
import org.openmrs.module.caremodels.config.OpenmrsReferenceChecker;
import org.openmrs.module.caremodels.config.SemanticValidator;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.config.StorageEnvelopeValidator;
import org.openmrs.module.caremodels.config.StaleRevisionException;
import org.openmrs.module.caremodels.config.ValidationResult;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;
import org.openmrs.module.caremodels.security.CareModelsPrivileges;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link CareModelService}.
 */
public class CareModelServiceImpl extends BaseOpenmrsService implements CareModelService {
	
	private static final Logger log = LoggerFactory.getLogger(CareModelServiceImpl.class);
	
	private CareModelDAO dao;
	
	public CareModelDAO getDao() {
		return dao;
	}
	
	public void setDao(CareModelDAO dao) {
		this.dao = dao;
	}
	
	@Override
	@Transactional(readOnly = true)
	public CareModel getCareModel(Integer careModelId) throws APIException {
		return dao.getCareModelById(careModelId);
	}
	
	@Override
	@Transactional(readOnly = true)
	public CareModel getCareModelByUuid(String uuid) throws APIException {
		return dao.getCareModelByUuid(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public CareModel getCareModelByCode(String code) throws APIException {
		return dao.getCareModelByCode(code);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<CareModel> getCareModels(String q, CareModelStatus status, boolean includeRetired, Integer startIndex,
	        Integer limit) throws APIException {
		return dao.getCareModels(q, status, includeRetired, startIndex, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public long getCareModelsCount(String q, CareModelStatus status, boolean includeRetired) throws APIException {
		return dao.getCareModelsCount(q, status, includeRetired);
	}
	
	@Override
	@Transactional
	public CareModel saveCareModel(CareModel careModel) throws APIException {
		if (careModel.getCode() == null || careModel.getCode().trim().isEmpty()) {
			throw new APIException("Care Model code is required");
		}
		if (careModel.getPrivilege() == null || careModel.getPrivilege().trim().isEmpty()) {
			// Fail closed: a model without a required view privilege is not publishable and cannot
			// be treated as public.
			throw new APIException("Care Model required view privilege is required");
		}
		if (careModel.getConfiguration() == null || careModel.getConfiguration().trim().isEmpty()) {
			throw new APIException("Care Model configuration envelope is required");
		}
		return dao.saveCareModel(careModel);
	}
	
	@Override
	@Transactional
	public void purgeCareModel(CareModel careModel) throws APIException {
		dao.purgeCareModel(careModel);
	}
	
	@Override
	@Transactional
	public CareModel retireCareModel(CareModel careModel, String reason) throws APIException {
		careModel.setRetired(true);
		careModel.setRetireReason(reason);
		careModel.setRetiredBy(Context.getAuthenticatedUser());
		careModel.setDateRetired(new Date());
		return dao.saveCareModel(careModel);
	}
	
	@Override
	@Transactional
	public CareModel unretireCareModel(CareModel careModel) throws APIException {
		careModel.setRetired(false);
		careModel.setRetireReason(null);
		careModel.setRetiredBy(null);
		careModel.setDateRetired(null);
		return dao.saveCareModel(careModel);
	}
	
	// ========================
	// Lifecycle: draft, validate, publish, duplicate, import/export (W2)
	// ========================
	
	private ObjectMapper objectMapper = new ObjectMapper().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
	
	private OpenmrsReferenceChecker referenceChecker = new OpenmrsReferenceChecker.OpenmrsApiReferenceChecker();
	
	private JsonSchemaValidator schemaValidator = new JsonSchemaValidator(objectMapper);
	
	private org.openmrs.module.caremodels.resolver.DataResolverRegistry resolverRegistry = new org.openmrs.module.caremodels.resolver.DataResolverRegistry();
	
	private SemanticValidator semanticValidator = new SemanticValidator(referenceChecker, resolverRegistry);
	
	public void setResolverRegistry(org.openmrs.module.caremodels.resolver.DataResolverRegistry registry) {
		resolverRegistry = registry;
		semanticValidator = new SemanticValidator(referenceChecker, registry);
	}
	
	/**
	 * Overridable for tests; production uses the OpenMRS API-backed checker.
	 */
	public void setReferenceChecker(OpenmrsReferenceChecker referenceChecker) {
		this.referenceChecker = referenceChecker;
		this.semanticValidator = new SemanticValidator(referenceChecker, resolverRegistry);
	}
	
	@Override
	@Transactional
	public CareModel createCareModel(String code, String name, String description, String privilege, String definitionJson)
	        throws APIException {
		if (code == null || code.trim().isEmpty()) {
			throw new APIException("Care Model code is required");
		}
		if (dao.getCareModelByCode(code) != null) {
			throw new APIException("A Care Model with code '" + code + "' already exists");
		}
		assertPrivilegePresent(privilege);
		assertDefinitionValid(definitionJson);
		
		CareModel model = new CareModel();
		model.setCode(code.trim());
		model.setName(name);
		model.setDescription(description);
		model.setPrivilege(privilege);
		model.setStatus(CareModelStatus.DRAFT);
		
		StorageEnvelope envelope = new StorageEnvelope();
		try {
			envelope.saveDraft(objectMapper.readTree(definitionJson), 0);
		}
		catch (IOException e) {
			throw new APIException("Definition is not valid JSON: " + e.getMessage(), e);
		}
		writeEnvelope(model, envelope);
		return dao.saveCareModel(model);
	}
	
	@Override
	@Transactional
	public CareModel saveDraft(String code, String definitionJson, Integer expectedRevision) throws APIException {
		assertDefinitionValid(definitionJson);
		CareModel model = getExistingModelByCodeForUpdate(code);
		
		StorageEnvelope envelope = readEnvelope(model);
		try {
			envelope.saveDraft(objectMapper.readTree(definitionJson), expectedRevision);
		}
		catch (IOException e) {
			throw new APIException("Definition is not valid JSON: " + e.getMessage(), e);
		}
		catch (StaleRevisionException e) {
			// propagate as-is; the REST layer maps it to a conflict response
			throw e;
		}
		writeEnvelope(model, envelope);
		return dao.saveCareModel(model);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ValidationResult validateDraft(String definitionJson) throws APIException {
		ValidationResult result = schemaValidator.validate(definitionJson, JsonSchemaValidator.SchemaType.MODEL_DEFINITION);
		if (result.isValid()) {
			try {
				result.merge(semanticValidator.validateDefinition(objectMapper.readTree(definitionJson)));
			}
			catch (IOException e) {
				result.addError("$", "Definition is not valid JSON: " + e.getMessage());
			}
		}
		return result;
	}
	
	@Override
	@Transactional
	public CareModel publishCareModel(String code, String version, Integer expectedRevision) throws APIException {
		// @Authorized is any-of; publication needs manage AND publish
		if (!Context.hasPrivilege(CareModelsPrivileges.PUBLISH_CARE_MODELS)) {
			throw new APIException("Publishing requires the " + CareModelsPrivileges.PUBLISH_CARE_MODELS + " privilege");
		}
		
		CareModel model = getExistingModelByCodeForUpdate(code);
		
		// MOD-007: a model whose required privilege is unregistered cannot publish
		assertPrivilegePresent(model.getPrivilege());
		if (!referenceChecker.privilegeExists(model.getPrivilege())) {
			throw new APIException("Cannot publish: required view privilege '" + model.getPrivilege()
			        + "' is not registered in OpenMRS");
		}
		
		StorageEnvelope envelope = readEnvelope(model);
		if (envelope.getDraft() == null || envelope.getDraft().getDefinition() == null) {
			throw new APIException("No draft to publish for model '" + code + "'");
		}
		
		// MOD-007 / AC12: full revalidation at publication time, not only at draft-save time
		String definitionJson = envelope.getDraft().getDefinition().toString();
		ValidationResult result = validateDraft(definitionJson);
		if (!result.isValid()) {
			throw new APIException("Cannot publish an invalid definition: " + result);
		}
		
		envelope.publishDraft(version, expectedRevision, new Date());
		model.setStatus(CareModelStatus.PUBLISHED);
		writeEnvelope(model, envelope);
		return dao.saveCareModel(model);
	}
	
	@Override
	@Transactional(readOnly = true)
	public String getPublishedDefinition(String code) throws APIException {
		StorageEnvelope envelope = readEnvelope(getExistingModelByCode(code));
		if (envelope.getPublished() == null) {
			return null;
		}
		return envelope.getPublished().getDefinition().toString();
	}
	
	@Override
	@Transactional(readOnly = true)
	public String getEnvelope(String code) throws APIException {
		return getExistingModelByCode(code).getConfiguration();
	}
	
	@Override
	@Transactional
	public CareModel duplicateFromPublished(String sourceCode, String newCode, String newName, String privilege)
	        throws APIException {
		if (dao.getCareModelByCode(newCode) != null) {
			throw new APIException("A Care Model with code '" + newCode + "' already exists");
		}
		assertPrivilegePresent(privilege);
		
		String published = getPublishedDefinition(sourceCode);
		if (published == null) {
			throw new APIException("Model '" + sourceCode + "' has no published version to duplicate");
		}
		assertDefinitionValid(published);
		
		CareModel duplicate = new CareModel();
		duplicate.setCode(newCode.trim());
		duplicate.setName(newName);
		duplicate.setPrivilege(privilege);
		duplicate.setStatus(CareModelStatus.DRAFT);
		
		StorageEnvelope envelope = new StorageEnvelope();
		try {
			envelope.saveDraft(objectMapper.readTree(published), 0);
		}
		catch (IOException e) {
			throw new APIException("Published definition is not valid JSON: " + e.getMessage(), e);
		}
		writeEnvelope(duplicate, envelope);
		return dao.saveCareModel(duplicate);
	}
	
	@Override
	@Transactional
	public CareModel importModel(String code, String name, String privilege, String envelopeJson) throws APIException {
		if (dao.getCareModelByCode(code) != null) {
			throw new APIException("A Care Model with code '" + code + "' already exists");
		}
		assertPrivilegePresent(privilege);
		
		ValidationResult result = schemaValidator.validate(envelopeJson, JsonSchemaValidator.SchemaType.STORAGE_ENVELOPE);
		if (!result.isValid()) {
			throw new APIException("Imported envelope is invalid: " + result);
		}
		
		StorageEnvelope envelope = StorageEnvelope.fromJson(objectMapper, envelopeJson);
		ValidationResult integrityResult = StorageEnvelopeValidator.validate(envelope);
		if (!integrityResult.isValid()) {
			throw new APIException("Imported envelope integrity check failed: " + integrityResult);
		}
		// every embedded definition must be valid against local resources
		if (envelope.getDraft() != null && envelope.getDraft().getDefinition() != null) {
			ValidationResult draftResult = validateDraft(envelope.getDraft().getDefinition().toString());
			if (!draftResult.isValid()) {
				throw new APIException("Imported draft is invalid: " + draftResult);
			}
		}
		if (envelope.getPublished() != null) {
			if (!referenceChecker.privilegeExists(privilege)) {
				throw new APIException("Cannot import a published model: required view privilege '" + privilege
				        + "' is not registered in OpenMRS");
			}
			ValidationResult publishedResult = validateDraft(envelope.getPublished().getDefinition().toString());
			if (!publishedResult.isValid()) {
				throw new APIException("Imported published definition is invalid: " + publishedResult);
			}
		}
		for (int i = 0; i < envelope.getHistory().size(); i++) {
			ValidationResult historyResult = validateDraft(envelope.getHistory().get(i).getDefinition().toString());
			if (!historyResult.isValid()) {
				throw new APIException("Imported history[" + i + "] definition is invalid: " + historyResult);
			}
		}
		
		CareModel model = new CareModel();
		model.setCode(code.trim());
		model.setName(name);
		model.setPrivilege(privilege);
		model.setStatus(envelope.getPublished() != null ? CareModelStatus.PUBLISHED : CareModelStatus.DRAFT);
		writeEnvelope(model, envelope);
		return dao.saveCareModel(model);
	}
	
	@Override
	@Transactional
	public CareModel deactivateCareModel(String code) throws APIException {
		CareModel model = getExistingModelByCode(code);
		if (model.getStatus() != CareModelStatus.PUBLISHED) {
			throw new APIException("Only PUBLISHED models can be deactivated; model '" + code + "' is " + model.getStatus());
		}
		model.setStatus(CareModelStatus.INACTIVE);
		return dao.saveCareModel(model);
	}
	
	@Override
	@Transactional
	public CareModel reactivateCareModel(String code) throws APIException {
		CareModel model = getExistingModelByCode(code);
		if (model.getStatus() != CareModelStatus.INACTIVE) {
			throw new APIException("Only INACTIVE models can be reactivated; model '" + code + "' is " + model.getStatus());
		}
		if (readEnvelope(model).getPublished() == null) {
			throw new APIException("Cannot reactivate model '" + code + "' without a published snapshot");
		}
		model.setStatus(CareModelStatus.PUBLISHED);
		return dao.saveCareModel(model);
	}
	
	// ========================
	// Internal helpers
	// ========================
	
	private CareModel getExistingModelByCode(String code) {
		if (code == null) {
			throw new APIException("Care Model code is required");
		}
		CareModel model = dao.getCareModelByCode(code);
		if (model == null) {
			throw new APIException("No Care Model with code '" + code + "'");
		}
		return model;
	}
	
	private CareModel getExistingModelByCodeForUpdate(String code) {
		if (code == null) {
			throw new APIException("Care Model code is required");
		}
		CareModel model = dao.getCareModelByCodeForUpdate(code);
		if (model == null) {
			throw new APIException("No Care Model with code '" + code + "'");
		}
		return model;
	}
	
	private StorageEnvelope readEnvelope(CareModel model) {
		return StorageEnvelope.fromJson(objectMapper, model.getConfiguration());
	}
	
	/**
	 * Persists the envelope back onto the entity; the entity revision column mirrors the envelope
	 * revision so callers can read it without parsing the JSON.
	 */
	private void writeEnvelope(CareModel model, StorageEnvelope envelope) {
		model.setConfiguration(envelope.toJson(objectMapper));
		model.setRevision(envelope.getRevision());
	}
	
	private void assertPrivilegePresent(String privilege) {
		if (privilege == null || privilege.trim().isEmpty()) {
			// fail closed: a model without a required view privilege is never public
			throw new APIException("Care Model required view privilege is required");
		}
	}
	
	private void assertDefinitionValid(String definitionJson) {
		ValidationResult result = validateDraft(definitionJson);
		if (!result.isValid()) {
			throw new APIException("Definition is invalid: " + result);
		}
	}
}
