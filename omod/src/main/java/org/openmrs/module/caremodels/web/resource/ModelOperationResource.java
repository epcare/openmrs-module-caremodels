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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.openmrs.module.caremodels.api.CareModelService;
import org.openmrs.module.caremodels.config.StaleRevisionException;
import org.openmrs.module.caremodels.config.ValidationResult;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ObjectNotFoundException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

/**
 * Model lifecycle operations for the admin designer (POST-only, following the module-family
 * operation-resource convention: the request body binds onto {@link ModelOperationRequest} and
 * {@code save()} dispatches on the {@code operation} field).
 * <ul>
 * <li>POST /ws/rest/v1/caremodels/modeloperation {"operation":"create", code, name, description,
 * privilege, definition}</li>
 * <li>{"operation":"saveDraft", code, definition, expectedRevision} — conflict payload on stale
 * revision</li>
 * <li>{"operation":"validate", definition} → {valid, errors:[{path,message}]}</li>
 * <li>{"operation":"publish", code, version, expectedRevision}</li>
 * <li>{"operation":"envelope"|"published", code} → stored JSON (export / workspace source)</li>
 * <li>{"operation":"duplicate", sourceCode, newCode, newName, privilege}</li>
 * <li>{"operation":"import", code, name, privilege, envelope}</li>
 * <li>{"operation":"deactivate"|"reactivate", code}</li>
 * </ul>
 * All operations delegate to {@link CareModelService}, which enforces manage/publish privileges and
 * fail-closed validation.
 */
@Resource(name = RestConstants.VERSION_1 + "/caremodels/modeloperation", supportedClass = ModelOperationRequest.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class ModelOperationResource extends DelegatingCrudResource<ModelOperationRequest> {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	private CareModelService service() {
		return org.openmrs.api.context.Context.getService(CareModelService.class);
	}
	
	@Override
	public ModelOperationRequest newDelegate() {
		return new ModelOperationRequest();
	}
	
	@Override
	public ModelOperationRequest save(ModelOperationRequest request) {
		String operation = request.getOperation() == null ? "" : request.getOperation();
		try {
			switch (operation) {
				case "create":
					return resultOf(service().createCareModel(request.getCode(), request.getName(),
					    request.getDescription(), request.getPrivilege(), request.getDefinition()));
				case "saveDraft":
					return resultOf(service().saveDraft(request.getCode(), request.getDefinition(),
					    request.getExpectedRevision()));
				case "validate":
					return validationResultOf(service().validateDraft(request.getDefinition()));
				case "publish":
					return resultOf(service().publishCareModel(request.getCode(), request.getVersion(),
					    request.getExpectedRevision()));
				case "envelope":
					return envelopeResultOf(service().getEnvelope(request.getCode()));
				case "published":
					return envelopeResultOf(service().getPublishedDefinition(request.getCode()));
				case "duplicate":
					return resultOf(service().duplicateFromPublished(request.getSourceCode(), request.getNewCode(),
					    request.getNewName(), request.getPrivilege()));
				case "import":
					return resultOf(service().importModel(request.getCode(), request.getName(), request.getPrivilege(),
					    request.getEnvelope()));
				case "deactivate":
					return resultOf(service().deactivateCareModel(request.getCode()));
				case "reactivate":
					return resultOf(service().reactivateCareModel(request.getCode()));
				default:
					throw new IllegalArgumentException("Unknown operation: " + operation);
			}
		}
		catch (StaleRevisionException e) {
			// surfaced as an explicit conflict so the designer can offer refresh-and-reapply
			ModelOperationRequest result = new ModelOperationRequest();
			result.setConflict(true);
			result.setExpectedRevision(e.getExpectedRevision());
			result.setCurrentRevision(e.getCurrentRevision());
			return result;
		}
	}
	
	private ModelOperationRequest resultOf(CareModel model) {
		ModelOperationRequest result = new ModelOperationRequest();
		result.setModelUuid(model.getUuid());
		result.setModelCode(model.getCode());
		result.setName(model.getName());
		result.setStatus(model.getStatus() != null ? model.getStatus().name() : null);
		result.setRevision(model.getRevision());
		return result;
	}
	
	private ModelOperationRequest envelopeResultOf(String json) {
		ModelOperationRequest result = new ModelOperationRequest();
		result.setEnvelope(json);
		return result;
	}
	
	private ModelOperationRequest validationResultOf(ValidationResult validationResult) {
		ModelOperationRequest result = new ModelOperationRequest();
		result.setValid(validationResult.isValid());
		StringBuilder errors = new StringBuilder();
		for (ValidationResult.Error error : validationResult.getErrors()) {
			if (errors.length() > 0) {
				errors.append('\n');
			}
			errors.append(error.getPath()).append(": ").append(error.getMessage());
		}
		result.setValidationErrors(errors.length() > 0 ? errors.toString() : null);
		return result;
	}
	
	@Override
	public ModelOperationRequest getByUniqueId(String uniqueId) {
		throw new ObjectNotFoundException();
	}
	
	@Override
	protected void delete(ModelOperationRequest delegate, String reason, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Delete is not supported for modeloperation");
	}
	
	@Override
	public void purge(ModelOperationRequest delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Purge is not supported for modeloperation");
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("List is not supported for modeloperation");
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addRequiredProperty("operation");
		description.addProperty("code");
		description.addProperty("name");
		description.addProperty("description");
		description.addProperty("privilege");
		description.addProperty("definition");
		description.addProperty("envelope");
		description.addProperty("expectedRevision");
		description.addProperty("version");
		description.addProperty("sourceCode");
		description.addProperty("newCode");
		description.addProperty("newName");
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
		// request/response fields mirror the delegate's properties
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("operation");
		description.addProperty("code");
		description.addProperty("name");
		description.addProperty("description");
		description.addProperty("privilege");
		description.addProperty("definition");
		description.addProperty("envelope");
		description.addProperty("expectedRevision");
		description.addProperty("version");
		description.addProperty("sourceCode");
		description.addProperty("newCode");
		description.addProperty("newName");
		description.addProperty("modelUuid");
		description.addProperty("modelCode");
		description.addProperty("status");
		description.addProperty("revision");
		description.addProperty("valid");
		description.addProperty("validationErrors");
		description.addProperty("conflict");
		description.addProperty("currentRevision");
		return description;
	}
	
	@Override
	public String getResourceVersion() {
		return "1.0";
	}
}
