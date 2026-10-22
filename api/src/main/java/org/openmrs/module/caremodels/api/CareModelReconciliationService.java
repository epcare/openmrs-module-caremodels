/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.api;

import java.util.Date;

import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.caremodels.dto.EnrollmentReconciliationResult;
import org.openmrs.module.caremodels.security.CareModelsPrivileges;
import org.springframework.transaction.annotation.Transactional;

/** Resumable missed-event recovery using a caller-held patient-id cursor. */
public interface CareModelReconciliationService extends OpenmrsService {

	@Transactional
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	EnrollmentReconciliationResult reconcile(Integer afterPatientId, Integer limit, Date asOf) throws APIException;
}
