/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.api.impl;

import org.openmrs.module.caremodels.model.CareModel;

/** Additional patient and model authorization for clinician-initiated enrollment commands. */
public interface EnrollmentCommandAuthorizer {

	void authorize(Integer patientId, CareModel model);
}
