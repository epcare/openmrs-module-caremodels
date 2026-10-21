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

import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Action;
import org.openmrs.module.caremodels.security.CareModelsPrivileges;
import org.springframework.transaction.annotation.Transactional;

/** Transactional, idempotent entry points for every supported membership enrollment flow. */
public interface CareModelEnrollmentService extends OpenmrsService {

	/** Applies a validated published rule decision. Replaying the same source UUID is a no-op. */
	@Transactional
	CareModelMember applyRuleEnrollment(Integer patientId, Integer careModelId, String ruleId, Action action,
	        String sourceUuid, Date asOf) throws APIException;

	/** Accepts a pending confirmation proposal and activates its episode. */
	@Transactional
	@Authorized(CareModelsPrivileges.TRANSITION_CARE_MODEL_MEMBERS)
	CareModelMember confirmEnrollment(String memberUuid, String reason, Date asOf) throws APIException;

	/** Declines a pending confirmation proposal while retaining its audit record. */
	@Transactional
	@Authorized(CareModelsPrivileges.TRANSITION_CARE_MODEL_MEMBERS)
	CareModelMember declineEnrollment(String memberUuid, String reason, Date asOf) throws APIException;

	/** Creates an ACTIVE manual episode only when the published definition permits it. */
	@Transactional
	@Authorized(CareModelsPrivileges.TRANSITION_CARE_MODEL_MEMBERS)
	CareModelMember enrollManually(Integer patientId, Integer careModelId, String reason, Date asOf) throws APIException;
}
