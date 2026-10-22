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

import java.util.List;

import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.CareModelService;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.AlreadyPaged;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

/**
 * Care Model registry CRUD for the admin designer. Endpoints:
 * <ul>
 * <li>GET /ws/rest/v1/caremodels/model?q=&amp;status=&amp;includeRetired= — searchable registry</li>
 * <li>GET /ws/rest/v1/caremodels/model/{uuid|code}</li>
 * <li>POST /ws/rest/v1/caremodels/model — create: {code, name, description, privilege, definition}</li>
 * <li>POST /ws/rest/v1/caremodels/model/{uuid|code} — update metadata / retire / unretire</li>
 * <li>DELETE /ws/rest/v1/caremodels/model/{uuid|code}?purge= — retire (default) or purge</li>
 * </ul>
 * The stored JSON envelope is deliberately NOT part of any representation; drafts travel through
 * the dedicated operation resource, so no registry listing can drag the configuration envelope
 * across the wire.
 */
@Resource(name = RestConstants.VERSION_1 + "/caremodels/model", supportedClass = CareModel.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class ModelResource extends DelegatingCrudResource<CareModel> {

	private CareModelService service() {
		return Context.getService(CareModelService.class);
	}

	@Override
	public CareModel newDelegate() {
		return new CareModel();
	}

	@Override
	public CareModel save(CareModel delegate) {
		// POST to the collection creates with an initial definition; POST to /{id} updates metadata
		if (delegate.getId() == null) {
			throw new IllegalArgumentException(
			        "Create through POST /caremodels/model requires the operation resource (code, name, privilege, definition)");
		}
		return service().saveCareModel(delegate);
	}

	@Override
	public CareModel getByUniqueId(String uuidOrCode) {
		if (uuidOrCode == null) {
			return null;
		}
		String value = uuidOrCode.trim();
		CareModel model = service().getCareModelByUuid(value);
		if (model == null) {
			model = service().getCareModelByCode(value);
		}
		return model;
	}

	/** RESTWS dispatches requests containing q/patient/model/tab filters through search(). */
	@Override
	protected PageableResult doSearch(RequestContext context) throws ResponseException {
		return doGetAll(context);
	}

	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		String q = trimToNull(context.getParameter("q"));
		String statusParam = trimToNull(context.getParameter("status"));
		boolean includeRetired = Boolean.parseBoolean(context.getParameter("includeRetired"));

		CareModelStatus status = null;
		if (statusParam != null) {
			status = CareModelStatus.valueOf(statusParam);
		}
		List<CareModel> models = service().getCareModels(q, status, includeRetired, context.getStartIndex(),
		    context.getLimit());
		long total = service().getCareModelsCount(q, status, includeRetired);
		return new AlreadyPaged<CareModel>(context, models, (long) context.getStartIndex() + models.size() < total, total);
	}

	@Override
	protected void delete(CareModel delegate, String reason, RequestContext context) throws ResponseException {
		service().retireCareModel(delegate, reason != null ? reason : "Retired through registry");
	}

	@Override
	public void purge(CareModel delegate, RequestContext context) throws ResponseException {
		service().purgeCareModel(delegate);
	}

	@Override
	public DelegatingResourceDescription getUpdatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("name");
		description.addProperty("description");
		description.addProperty("privilege");
		return description;
	}

	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("uuid");
		description.addProperty("code");
		description.addProperty("name");
		description.addProperty("description");
		description.addProperty("privilege");
		description.addProperty("status");
		description.addProperty("revision");
		description.addProperty("retired");
		return description;
	}

	// No configuration exposure on purpose: the envelope only travels through the
	// envelope/operation resources.

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
