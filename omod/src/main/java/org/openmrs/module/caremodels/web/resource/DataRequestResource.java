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

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.CareModelAccessService;
import org.openmrs.module.caremodels.api.CareModelAccessService.AuthorizedAccess;
import org.openmrs.module.caremodels.resolver.DataResolver;
import org.openmrs.module.caremodels.resolver.DataResolverRegistry;
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
 * Guarded clinical data endpoint. Only invoked by the frontend for a widget that is visible in the
 * currently selected inner tab; independently re-validates the full access chain on every call and
 * refuses any data source that is not declared in that tab's published definition. POST
 * /ws/rest/v1/caremodels/data {"patient", "model", "tab", "dataSource"}
 */
@Resource(name = RestConstants.VERSION_1 + "/caremodels/data", supportedClass = DataRequestResult.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class DataRequestResource extends DelegatingCrudResource<DataRequestResult> {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	private static final DataResolverRegistry RESOLVERS = new DataResolverRegistry();
	
	@Override
	public DataRequestResult newDelegate() {
		return new DataRequestResult();
	}
	
	@Override
	public DataRequestResult save(DataRequestResult request) {
		if (request.getPatient() == null || request.getModel() == null || request.getTab() == null
		        || request.getDataSource() == null) {
			throw new IllegalArgumentException("patient, model, tab and dataSource are required");
		}
		
		// 1. full authorization chain, fresh on every request
		AuthorizedAccess access = Context.getService(CareModelAccessService.class).authorizeWorkspace(request.getPatient(),
		    request.getModel(), request.getTab());
		
		// 2. the requested source must be declared on a widget of THIS visible tab
		JsonNode source = findDeclaredSource(access, request.getDataSource());
		if (source == null) {
			throw new APIException("Data source is not declared in the requested tab");
		}
		
		// 3. resolve the alias to a real concept uuid and invoke the registered resolver
		String resolverId = source.path("type").asText();
		DataResolver resolver = RESOLVERS.getResolver(resolverId);
		if (resolver == null) {
			throw new APIException("No resolver is registered for data source type: " + resolverId);
		}
		String conceptUuid = resolveConcept(access, source.path("conceptAlias").asText(null));
		
		JsonNode result = resolver.resolve(patientIdFor(access.getPatientUuid()), source, conceptUuid);
		
		DataRequestResult response = new DataRequestResult();
		response.setModelCode(access.getModelCode());
		response.setMemberUuid(access.getMemberUuid());
		response.setResultJson(result.toString());
		return response;
	}
	
	private JsonNode findDeclaredSource(AuthorizedAccess access, String dataSourceKey) {
		JsonNode dataSources = access.getPublishedDefinition().path("dataSources");
		if (!dataSources.has(dataSourceKey)) {
			return null;
		}
		JsonNode source = dataSources.path(dataSourceKey);
		// the source must be referenced by a widget in the requested tab; a declared-but-unbound
		// source is not fetchable
		for (JsonNode widget : access.getTabDefinition().path("widgets")) {
			if (dataSourceKey.equals(widget.path("dataSource").asText(null))) {
				return source;
			}
		}
		return null;
	}
	
	private String resolveConcept(AuthorizedAccess access, String conceptAlias) {
		if (conceptAlias == null) {
			return null;
		}
		return access.getPublishedDefinition().path("terminology").path("concepts").path(conceptAlias).asText(null);
	}
	
	private Integer patientIdFor(String patientUuid) {
		org.openmrs.Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		return patient != null ? patient.getPatientId() : null;
	}
	
	@Override
	public DataRequestResult getByUniqueId(String uniqueId) {
		throw new ObjectNotFoundException();
	}
	
	@Override
	protected void delete(DataRequestResult delegate, String reason, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Delete is not supported for data");
	}
	
	@Override
	public void purge(DataRequestResult delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Purge is not supported for data");
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("List is not supported for data; use POST");
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("patient");
		description.addProperty("model");
		description.addProperty("tab");
		description.addProperty("dataSource");
		description.addProperty("modelCode");
		description.addProperty("memberUuid");
		description.addProperty("resultJson");
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
