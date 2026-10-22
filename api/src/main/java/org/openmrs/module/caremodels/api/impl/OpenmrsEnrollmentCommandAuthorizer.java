/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.api.impl;

import org.openmrs.Patient;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.security.CareModelsPrivileges;

/** Fail-closed OpenMRS privilege checks for clinician-initiated membership changes. */
public class OpenmrsEnrollmentCommandAuthorizer implements EnrollmentCommandAuthorizer {

	@Override
	public void authorize(Integer patientId, CareModel model) {
		if (!Context.hasPrivilege(CareModelsPrivileges.VIEW_CARE_MODELS)) {
			throw new APIException("Care Model membership is not authorized");
		}
		Patient patient = patientId == null ? null : Context.getPatientService().getPatient(patientId);
		if (patient == null || Boolean.TRUE.equals(patient.getVoided())
		        || (!Context.hasPrivilege("View Patients") && !Context.hasPrivilege("Get Patients"))) {
			throw new APIException("Care Model membership is not authorized");
		}
		String privilege = model == null ? null : model.getPrivilege();
		if (privilege == null || privilege.trim().isEmpty() || !Context.hasPrivilege(privilege)) {
			throw new APIException("Care Model membership is not authorized");
		}
	}
}
