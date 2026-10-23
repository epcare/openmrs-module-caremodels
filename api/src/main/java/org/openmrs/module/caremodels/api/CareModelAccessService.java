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

import org.openmrs.api.APIException;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.caremodels.dto.CareModelMembershipDTO;
import org.openmrs.module.caremodels.security.CareModelsPrivileges;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side authorization chain for every protected workspace, data and action request:
 * <ol>
 * <li>master privilege ({@link CareModelsPrivileges#VIEW_CARE_MODELS});</li>
 * <li>patient access (patient exists, caller may view patients);</li>
 * <li>ACTIVE non-voided membership lookup (indexed patient_id + status);</li>
 * <li>join model metadata and filter on: not retired, status PUBLISHED, and the caller holds the
 * model's required view privilege ({@code care_model.privilege});</li>
 * <li>on model/tab selection, revalidate all of the above before any definition or clinical data is
 * loaded.</li>
 * </ol>
 * Every method fails closed. Clients cannot bypass these checks by calling endpoints directly; the
 * REST layer delegates all authorization to this service on each request.
 */
public interface CareModelAccessService extends OpenmrsService {
	
	/**
	 * Throws an {@link APIException} when the current user lacks the master privilege. The
	 * membership listing must not be reachable without it.
	 */
	@Transactional(readOnly = true)
	void requireMasterPrivilege() throws APIException;
	
	/**
	 * Resolves the patient and verifies the caller may access the patient's record; fails closed
	 * for unknown patients.
	 */
	@Transactional(readOnly = true)
	String requirePatientAccess(String patientUuid) throws APIException;
	
	/**
	 * The authorized ACTIVE memberships of a patient: safe metadata only, filtered by master
	 * privilege, patient access, model status and model privilege. Enacts the full authorization
	 * chain; never selects the model configuration envelope and never invokes clinical resolvers.
	 */
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.VIEW_CARE_MODELS)
	List<CareModelMembershipDTO> getAuthorizedMemberships(String patientUuid) throws APIException;
	
	/**
	 * Full revalidation for a workspace or data request: master privilege, patient access, ACTIVE
	 * non-voided membership, PUBLISHED non-retired model, model privilege, and that the resolved
	 * tab exists in the published definition. A null tabId resolves the definition's default tab.
	 * 
	 * @return the authorized view: the membership, the resolved tab's definition and the tab
	 *         summary of the whole definition
	 */
	@Transactional(readOnly = true)
	AuthorizedAccess authorizeWorkspace(String patientUuid, String modelCodeOrUuid, String tabId) throws APIException;
	
	/**
	 * Result of a successful authorization: everything the caller may touch, and nothing more.
	 */
	class AuthorizedAccess {
		
		private com.fasterxml.jackson.databind.JsonNode memberPathway;
		
		public com.fasterxml.jackson.databind.JsonNode getMemberPathway() {
			return memberPathway;
		}
		
		/** Internal authorized projection only; no metadata is accepted from a REST request. */
		public void setMemberPathway(com.fasterxml.jackson.databind.JsonNode memberPathway) {
			this.memberPathway = memberPathway;
		}
		
		private final String patientUuid;
		
		private final String modelCode;
		
		private final String modelUuid;
		
		private final String memberUuid;
		
		private final com.fasterxml.jackson.databind.JsonNode publishedDefinition;
		
		private final com.fasterxml.jackson.databind.JsonNode tabDefinition;
		
		/** Id + label of every tab in the definition, in authored order. */
		private final com.fasterxml.jackson.databind.JsonNode tabs;
		
		public AuthorizedAccess(String patientUuid, String modelCode, String modelUuid, String memberUuid,
		    com.fasterxml.jackson.databind.JsonNode publishedDefinition,
		    com.fasterxml.jackson.databind.JsonNode tabDefinition, com.fasterxml.jackson.databind.JsonNode tabs) {
			this.patientUuid = patientUuid;
			this.modelCode = modelCode;
			this.modelUuid = modelUuid;
			this.memberUuid = memberUuid;
			this.publishedDefinition = publishedDefinition;
			this.tabDefinition = tabDefinition;
			this.tabs = tabs;
		}
		
		public com.fasterxml.jackson.databind.JsonNode getTabs() {
			return tabs;
		}
		
		public String getPatientUuid() {
			return patientUuid;
		}
		
		public String getModelCode() {
			return modelCode;
		}
		
		public String getModelUuid() {
			return modelUuid;
		}
		
		public String getMemberUuid() {
			return memberUuid;
		}
		
		/** The full published definition; only for the selected model. */
		public com.fasterxml.jackson.databind.JsonNode getPublishedDefinition() {
			return publishedDefinition;
		}
		
		/** The requested inner tab, or the whole definition when no tab was requested. */
		public com.fasterxml.jackson.databind.JsonNode getTabDefinition() {
			return tabDefinition;
		}
	}
}
