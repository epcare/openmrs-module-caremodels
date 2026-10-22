/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.web.resource;

import java.util.Date;
import java.util.Locale;

import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.CareModelEnrollmentService;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.model.CareModelMember.MemberStatus;
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
 * POST-only command boundary for manual enrollment, confirmation decisions and audited episode
 * transitions. Authorization and state-machine enforcement remain in the enrollment service.
 */
@Resource(name = RestConstants.VERSION_1 + "/caremodels/membershipoperation", supportedClass = MembershipOperationRequest.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class MembershipOperationResource extends DelegatingCrudResource<MembershipOperationRequest> {

	protected CareModelEnrollmentService service() {
		return Context.getService(CareModelEnrollmentService.class);
	}

	@Override
	public MembershipOperationRequest newDelegate() {
		return new MembershipOperationRequest();
	}

	@Override
	public MembershipOperationRequest save(MembershipOperationRequest request) {
		String operation = normalized(request.getOperation());
		Date now = new Date();
		switch (operation) {
			case "manual":
				return resultOf(service().enrollManually(request.getPatient(), request.getModel(), request.getReason(), now));
			case "confirm":
				return resultOf(service().confirmEnrollment(request.getMemberUuid(), request.getReason(), now));
			case "decline":
				return resultOf(service().declineEnrollment(request.getMemberUuid(), request.getReason(), now));
			case "transition":
				return resultOf(service().transitionEnrollment(request.getMemberUuid(), statusFor(request.getTransition()),
				    request.getReason(), now));
			default:
				throw new IllegalArgumentException("Unknown membership operation: " + operation);
		}
	}

	private MemberStatus statusFor(String transition) {
		String value = normalized(transition).toUpperCase(Locale.ENGLISH);
		switch (value) {
			case "SUSPEND":
				return MemberStatus.SUSPENDED;
			case "RESUME":
				return MemberStatus.ACTIVE;
			case "COMPLETE":
				return MemberStatus.COMPLETED;
			case "WITHDRAW":
				return MemberStatus.WITHDRAWN;
			default:
				throw new IllegalArgumentException("Unknown membership transition: " + value);
		}
	}

	private MembershipOperationRequest resultOf(CareModelMember member) {
		MembershipOperationRequest result = new MembershipOperationRequest();
		result.setMemberUuid(member.getUuid());
		result.setStatus(member.getStatus() == null ? null : member.getStatus().name());
		result.setEpisodeNumber(member.getEpisodeNumber());
		result.setDateEnrolled(member.getDateEnrolled());
		result.setDateCompleted(member.getDateCompleted());
		result.setEnrollmentSource(member.getEnrollmentSource());
		return result;
	}

	@Override
	public MembershipOperationRequest getByUniqueId(String uniqueId) {
		throw new ObjectNotFoundException();
	}

	@Override
	protected void delete(MembershipOperationRequest delegate, String reason, RequestContext context)
	        throws ResponseException {
		throw new UnsupportedOperationException("Delete is not supported for membershipoperation");
	}

	@Override
	public void purge(MembershipOperationRequest delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Purge is not supported for membershipoperation");
	}

	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("List is not supported for membershipoperation");
	}

	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("operation");
		description.addProperty("patient");
		description.addProperty("model");
		description.addProperty("memberUuid");
		description.addProperty("reason");
		description.addProperty("transition");
		description.addProperty("status");
		description.addProperty("episodeNumber");
		description.addProperty("dateEnrolled");
		description.addProperty("dateCompleted");
		description.addProperty("enrollmentSource");
		return description;
	}

	@Override
	public String getResourceVersion() {
		return "1.0";
	}

	private static String normalized(String value) {
		return value == null ? "" : value.trim();
	}
}
