/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

/** Receives clinical changes only after their enclosing transaction commits. */
public interface ClinicalChangeListener {

	void onClinicalChange(ClinicalChangeEvent event);
}
