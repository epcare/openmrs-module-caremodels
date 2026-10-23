/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.web.resource;

import java.util.Date;

import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.CareModelReconciliationService;
import org.openmrs.module.caremodels.dto.EnrollmentReconciliationResult;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ObjectNotFoundException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

/** POST-only, cursor-based missed-event recovery endpoint for administrators. */
@Resource(name = RestConstants.VERSION_1 + "/caremodels/reconciliationoperation", supportedClass = ReconciliationOperationRequest.class, supportedOpenmrsVersions = { "1.8 - 9.0.*" })
public class ReconciliationOperationResource extends DelegatingCrudResource<ReconciliationOperationRequest> {
	
	protected CareModelReconciliationService service() {
		return Context.getService(CareModelReconciliationService.class);
	}
	
	@Override
	public ReconciliationOperationRequest newDelegate() {
		return new ReconciliationOperationRequest();
	}
	
	@Override
	public ReconciliationOperationRequest save(ReconciliationOperationRequest request) {
		Date asOf = request.getAsOf() == null ? new Date() : request.getAsOf();
		EnrollmentReconciliationResult batch = service().reconcile(request.getAfterPatientId(), request.getLimit(), asOf);
		ReconciliationOperationRequest result = new ReconciliationOperationRequest();
		result.setAsOf(asOf);
		result.setNextAfterPatientId(batch.getNextAfterPatientId());
		result.setProcessedPatients(batch.getProcessedPatients());
		result.setEvaluatedModels(batch.getEvaluatedModels());
		result.setAppliedDecisions(batch.getAppliedDecisions());
		result.setFailedEvaluations(batch.getFailedEvaluations());
		result.setHasMore(batch.isHasMore());
		return result;
	}
	
	@Override
	public ReconciliationOperationRequest getByUniqueId(String uniqueId) {
		throw new ObjectNotFoundException();
	}
	
	@Override
	protected void delete(ReconciliationOperationRequest delegate, String reason, RequestContext context)
	        throws ResponseException {
		throw new UnsupportedOperationException("Delete is not supported for reconciliationoperation");
	}
	
	@Override
	public void purge(ReconciliationOperationRequest delegate, RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("Purge is not supported for reconciliationoperation");
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		throw new UnsupportedOperationException("List is not supported for reconciliationoperation");
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("asOf");
		description.addProperty("afterPatientId");
		description.addProperty("limit");
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("asOf");
		description.addProperty("afterPatientId");
		description.addProperty("limit");
		return description;
	}

	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("asOf");
		description.addProperty("afterPatientId");
		description.addProperty("limit");
		description.addProperty("nextAfterPatientId");
		description.addProperty("processedPatients");
		description.addProperty("evaluatedModels");
		description.addProperty("appliedDecisions");
		description.addProperty("failedEvaluations");
		description.addProperty("hasMore");
		return description;
	}
	
	@Override
	public String getResourceVersion() {
		return "1.0";
	}
}
