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

import java.util.Date;
import java.util.List;

import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.model.CareModelMember.MemberStatus;
import org.openmrs.module.caremodels.security.CareModelsPrivileges;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for Care Model membership episodes. Only this service may create or transition episodes;
 * it enforces one current (ACTIVE/SUSPENDED, non-voided) episode per patient and model, backed by
 * the generated-column unique index.
 */
public interface CareModelMemberService extends OpenmrsService {
	
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.VIEW_CARE_MODELS)
	CareModelMember getMember(Integer careModelMemberId) throws APIException;
	
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.VIEW_CARE_MODELS)
	CareModelMember getMemberByUuid(String uuid) throws APIException;
	
	/**
	 * The current (ACTIVE/SUSPENDED, non-voided) episode for a patient in a model, if any. Used by
	 * the membership listing; callers must enforce patient access and model privileges themselves.
	 */
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.VIEW_CARE_MODELS)
	CareModelMember getCurrentMember(Integer patientId, Integer careModelId) throws APIException;
	
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.VIEW_CARE_MODELS)
	List<CareModelMember> getMembersByPatientAndModel(Integer patientId, Integer careModelId) throws APIException;
	
	/** ACTIVE non-voided episodes for a patient; the sole source of patient outer tabs. */
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.VIEW_CARE_MODELS)
	List<CareModelMember> getActiveMembersByPatient(Integer patientId) throws APIException;
	
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.VIEW_CARE_MODELS)
	List<CareModelMember> getActiveMembersByModel(Integer careModelId) throws APIException;
	
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.VIEW_CARE_MODELS)
	List<CareModelMember> getMembersByPatientAndStatus(Integer patientId, MemberStatus status, boolean includeVoided,
	        Integer startIndex, Integer limit) throws APIException;
	
	@Transactional(readOnly = true)
	@Authorized(CareModelsPrivileges.VIEW_CARE_MODELS)
	long getMembersCount(Integer patientId, MemberStatus status, boolean includeVoided) throws APIException;
	
	/**
	 * Saves an episode. Callers creating an ACTIVE episode must have verified that no current
	 * episode exists; the database unique index is the final concurrency backstop.
	 */
	@Transactional
	@Authorized(CareModelsPrivileges.TRANSITION_CARE_MODEL_MEMBERS)
	CareModelMember saveMember(CareModelMember member) throws APIException;
	
	/** Manual episode transition (e.g. suspend, complete, withdraw) with a required audited reason. */
	@Transactional
	@Authorized(CareModelsPrivileges.TRANSITION_CARE_MODEL_MEMBERS)
	CareModelMember transitionMember(CareModelMember member, MemberStatus newStatus, String reason, Date asOf)
	        throws APIException;
	
	@Transactional
	@Authorized(CareModelsPrivileges.TRANSITION_CARE_MODEL_MEMBERS)
	CareModelMember voidMember(CareModelMember member, String reason) throws APIException;
	
	@Transactional
	@Authorized(CareModelsPrivileges.TRANSITION_CARE_MODEL_MEMBERS)
	CareModelMember unvoidMember(CareModelMember member) throws APIException;
	
	@Transactional
	@Authorized(CareModelsPrivileges.TRANSITION_CARE_MODEL_MEMBERS)
	void purgeMember(CareModelMember member) throws APIException;
}
