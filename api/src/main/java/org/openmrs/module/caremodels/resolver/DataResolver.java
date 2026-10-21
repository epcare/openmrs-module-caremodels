/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.resolver;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A registered, safe clinical data resolver. Resolvers are code extensions with stable ids;
 * definitions can only name resolvers that exist here, and the guarded data endpoint invokes a
 * resolver only after the full access chain has passed and the source has been proven to be
 * declared in the requested visible tab.
 * <p>
 * Contract: never return voided observations by default, respect the declared result cap and date
 * window, and include effective time, units and provenance in the result.
 */
public interface DataResolver {
	
	/** Stable resolver id, matching the data source {@code type} for generic sources. */
	String getId();
	
	/**
	 * Executes the query.
	 * 
	 * @param patientId the authorized patient's internal id
	 * @param source the data source declaration from the published definition
	 * @param resolvedConceptUuid the OpenMRS concept uuid the declared alias resolves to, or null
	 *            for sources without a concept
	 * @return result node with value(s), type, units, effective time and provenance
	 */
	JsonNode resolve(Integer patientId, JsonNode source, String resolvedConceptUuid);
}
