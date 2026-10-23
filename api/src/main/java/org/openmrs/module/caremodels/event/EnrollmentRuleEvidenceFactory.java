/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;

import org.openmrs.module.caremodels.rule.RuleEvidenceProvider;

/** Creates patient-scoped evidence providers for one published model evaluation. */
public interface EnrollmentRuleEvidenceFactory {
	
	Integer resolvePatientId(String patientUuid);
	
	RuleEvidenceProvider create(Integer patientId, JsonNode definition, Date asOf);
}
