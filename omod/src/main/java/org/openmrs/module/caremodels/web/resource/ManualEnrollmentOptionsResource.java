package org.openmrs.module.caremodels.web.resource;

import java.util.Collections;
import java.util.List;

import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.CareModelEnrollmentService;
import org.openmrs.module.caremodels.dto.ManualEnrollmentOption;
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
 * Read-only discovery and eligibility evaluation for manual enrollment. Enrollment itself uses POST
 * /caremodels/membershipoperation with operation "manual", which re-validates everything inside its
 * transaction.
 */
@Resource(name = RestConstants.VERSION_1 + "/caremodels/manualenrollmentoptions", supportedClass = ManualEnrollmentOption.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class ManualEnrollmentOptionsResource extends DelegatingCrudResource<ManualEnrollmentOption> {
	
	protected CareModelEnrollmentService service() {
		return Context.getService(CareModelEnrollmentService.class);
	}
	
	@Override
	public ManualEnrollmentOption newDelegate() {
		return new ManualEnrollmentOption();
	}
	
	@Override
	public ManualEnrollmentOption save(ManualEnrollmentOption delegate) {
		throw new UnsupportedOperationException(
		        "Enrollment uses POST /caremodels/membershipoperation with operation \"manual\"");
	}
	
	@Override
	protected void delete(ManualEnrollmentOption delegate, String reason, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Delete is not supported for enrollment options");
	}
	
	@Override
	public void purge(ManualEnrollmentOption delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Purge is not supported for enrollment options");
	}
	
	// RESTWS dispatches collection GETs carrying custom query parameters (patient, model) to
	// doSearch, not doGetAll — route them back to the controlled listing (same as MembershipResource).
	@Override
	protected PageableResult doSearch(RequestContext context) throws ResponseException {
		return doGetAll(context);
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		String patientUuid = trimToNull(context.getParameter("patient"));
		if (patientUuid == null) {
			throw new IllegalArgumentException("The patient parameter is required");
		}
		String model = trimToNull(context.getParameter("model"));
		if (model != null) {
			List<ManualEnrollmentOption> single = Collections.singletonList(service().evaluateManualEnrollment(patientUuid,
			    model));
			return new NeedsPaging<ManualEnrollmentOption>(single, context);
		}
		return new NeedsPaging<ManualEnrollmentOption>(service().listManualEnrollmentOptions(patientUuid), context);
	}
	
	@Override
	public ManualEnrollmentOption getByUniqueId(String uniqueId) {
		throw new UnsupportedOperationException("Options are listed per patient; use ?patient={uuid}");
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("modelUuid");
		description.addProperty("modelCode");
		description.addProperty("displayLabel");
		if (representation instanceof DefaultRepresentation) {
			description.addProperty("category");
			description.addProperty("requiresReason");
			description.addProperty("alreadyEnrolled");
			description.addProperty("openStatus");
			description.addProperty("eligible");
			description.addProperty("reason");
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
