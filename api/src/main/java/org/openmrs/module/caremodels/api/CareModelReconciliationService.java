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
import org.openmrs.module.caremodels.dto.EnrollmentReconciliationResult;
import org.openmrs.module.caremodels.security.CareModelsPrivileges;
import org.springframework.transaction.annotation.Transactional;

/** Background/admin-only missed-event recovery. Never called by membership or workspace reads. */
public interface CareModelReconciliationService extends OpenmrsService {

	/** Advances the durable background checkpoint by one bounded model/patient batch. */
	@Transactional(timeout = 30)
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	int reconcileNextBatch(int limit);

	/** Caller-managed backfill. Reuse asOf when retrying the same logical run. */
	@Transactional(timeout = 30)
	@Authorized(CareModelsPrivileges.MANAGE_CARE_MODELS)
	EnrollmentReconciliationResult reconcile(Integer afterPatientId, Integer limit, Date asOf) throws APIException;
}
