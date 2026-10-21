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
import org.openmrs.module.caremodels.api.CareModelAccessService;
import org.openmrs.module.caremodels.dto.CareModelMembershipDTO;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

/**
 * Patient membership listing — the sole source of outer patient tabs. GET
 * /ws/rest/v1/caremodels/membership?patient={patientUuid} Returns ONLY authorized ACTIVE non-voided
 * memberships as safe metadata. The authorization chain (master privilege, patient access, model
 * status, model privilege) runs in {@link CareModelAccessService}; the model configuration envelope
 * is never selected and no clinical resolver is ever invoked on this path. A caller without the
 * master privilege gets the same failure as any other denial — restricted model names are never
 * disclosed.
 */
@Resource(name = RestConstants.VERSION_1 + "/caremodels/membership", supportedClass = CareModelMembershipDTO.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class MembershipResource extends DelegatingCrudResource<CareModelMembershipDTO> {
	
	@Override
	public CareModelMembershipDTO newDelegate() {
		return new CareModelMembershipDTO();
	}
	
	@Override
	public CareModelMembershipDTO save(CareModelMembershipDTO delegate) {
		throw new UnsupportedOperationException("Memberships are managed by the enrollment engine");
	}
	
	@Override
	public CareModelMembershipDTO getByUniqueId(String uniqueId) {
		throw new UnsupportedOperationException("Memberships are listed per patient; use ?patient={uuid}");
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		String patientUuid = trimToNull(context.getParameter("patient"));
		if (patientUuid == null) {
			throw new IllegalArgumentException("The patient parameter is required");
		}
		List<CareModelMembershipDTO> memberships = Context.getService(CareModelAccessService.class)
		        .getAuthorizedMemberships(patientUuid);
		return new NeedsPaging<CareModelMembershipDTO>(memberships, context);
	}
	
	@Override
	protected void delete(CareModelMembershipDTO delegate, String reason, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Delete is not supported for membership");
	}
	
	@Override
	public void purge(CareModelMembershipDTO delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Purge is not supported for membership");
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("memberUuid");
		description.addProperty("modelUuid");
		description.addProperty("modelCode");
		description.addProperty("modelName");
		description.addProperty("displayLabel");
		description.addProperty("episodeNumber");
		if (representation instanceof DefaultRepresentation) {
			description.addProperty("dateEnrolled");
		}
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
