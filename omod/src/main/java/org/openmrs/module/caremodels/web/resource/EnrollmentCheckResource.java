package org.openmrs.module.caremodels.web.resource;

import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.CareModelEnrollmentService;
import org.openmrs.module.caremodels.dto.ManualEnrollmentOption;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

/**
 * Explicit eligibility evaluation for manual enrollment (POST /caremodels/enrollmentcheck). Runs
 * the model's own enrollment policy on request only; never persists and never consults clinical
 * observations. Enrollment still re-validates the policy inside its transaction.
 */
@Resource(name = RestConstants.VERSION_1 + "/caremodels/enrollmentcheck", supportedClass = EnrollmentCheckRequest.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class EnrollmentCheckResource extends DelegatingCrudResource<EnrollmentCheckRequest> {
	
	protected CareModelEnrollmentService service() {
		return Context.getService(CareModelEnrollmentService.class);
	}
	
	@Override
	public EnrollmentCheckRequest newDelegate() {
		return new EnrollmentCheckRequest();
	}
	
	@Override
	public EnrollmentCheckRequest save(EnrollmentCheckRequest request) {
		ManualEnrollmentOption option = service().evaluateManualEnrollment(request.getPatient(), request.getModel());
		request.setModelCode(option.getModelCode());
		if (option.isAlreadyEnrolled()) {
			request.setStatus("PENDING".equals(option.getOpenStatus()) ? "REQUIRES_CONFIRMATION" : "ALREADY_ENROLLED");
			request.setCanEnroll(option.isEligible());
		} else if (option.isEligible()) {
			request.setStatus("ELIGIBLE");
			request.setCanEnroll(true);
		} else {
			request.setStatus("NOT_ELIGIBLE");
			request.setCanEnroll(false);
		}
		request.setRequiresReason(option.isRequiresReason());
		request.setRequiresConfirmation(option.isRequiresConfirmation());
		if (option.getReason() != null) {
			request.getReasons().add(option.getReason());
		}
		return request;
	}
	
	@Override
	protected void delete(EnrollmentCheckRequest delegate, String reason, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Delete is not supported for enrollment checks");
	}
	
	@Override
	public void purge(EnrollmentCheckRequest delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Purge is not supported for enrollment checks");
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addRequiredProperty("patient");
		description.addRequiredProperty("model");
		return description;
	}
	
	@Override
	public EnrollmentCheckRequest getByUniqueId(String uniqueId) {
		throw new UnsupportedOperationException("Enrollment checks are submitted per patient and model");
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("modelCode");
		description.addProperty("status");
		if (representation instanceof DefaultRepresentation) {
			description.addProperty("canEnroll");
			description.addProperty("requiresReason");
			description.addProperty("requiresConfirmation");
			description.addProperty("reasons");
		}
		return description;
	}
	
	@Override
	public String getResourceVersion() {
		return "1.0";
	}
}
