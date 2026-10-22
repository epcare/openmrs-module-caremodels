/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.api.db;

import java.util.List;

/** Bounded cursor scan over core OpenMRS patients; no module-owned work queue is required. */
public interface EnrollmentReconciliationDAO {

	List<Integer> getPatientIdsAfter(Integer afterPatientId, int limit);
}
