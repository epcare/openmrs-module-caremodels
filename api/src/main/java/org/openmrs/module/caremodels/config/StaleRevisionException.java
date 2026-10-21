/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.config;

/**
 * Thrown when a draft write or publication carries a revision that does not match the current
 * stored revision: the caller's view is stale and must be refreshed before retrying.
 */
public class StaleRevisionException extends RuntimeException {
	
	private static final long serialVersionUID = 1L;
	
	private final Integer expectedRevision;
	
	private final Integer currentRevision;
	
	public StaleRevisionException(Integer expectedRevision, Integer currentRevision) {
		super("Stale revision: expected " + expectedRevision + " but current revision is " + currentRevision
		        + ". Refresh the draft and re-apply changes.");
		this.expectedRevision = expectedRevision;
		this.currentRevision = currentRevision;
	}
	
	public Integer getExpectedRevision() {
		return expectedRevision;
	}
	
	public Integer getCurrentRevision() {
		return currentRevision;
	}
}
