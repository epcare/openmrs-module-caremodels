/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import java.util.Date;

import org.openmrs.api.context.Context;

abstract class ClinicalChangeAdviceSupport {
	
	private ClinicalChangePublisher publisher;
	
	ClinicalChangeAdviceSupport() {
	}
	
	ClinicalChangeAdviceSupport(ClinicalChangePublisher publisher) {
		this.publisher = publisher;
	}
	
	void publish(ClinicalChangeEvent.TriggerType type, String patientUuid, String sourceUuid, String referenceUuid) {
		publish(type, patientUuid, sourceUuid, referenceUuid, null);
	}
	
	void publish(ClinicalChangeEvent.TriggerType type, String patientUuid, String sourceUuid, String referenceUuid,
	        String reviewSourceUuid) {
		if (patientUuid != null && sourceUuid != null && referenceUuid != null) {
			publisher().publishAfterCommit(
			    new ClinicalChangeEvent(type, patientUuid, sourceUuid, referenceUuid, new Date(), reviewSourceUuid));
		}
	}
	
	private ClinicalChangePublisher publisher() {
		if (publisher == null) {
			publisher = Context.getRegisteredComponent("caremodels.ClinicalChangePublisher", ClinicalChangePublisher.class);
		}
		return publisher;
	}
}
