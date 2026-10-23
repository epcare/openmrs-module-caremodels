/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.web.resource;

import java.util.Date;

/** Request and response fields for one bounded reconciliation batch. */
public class ReconciliationOperationRequest {

	private Date asOf;

	public Date getAsOf() {
		return asOf;
	}

	public void setAsOf(Date asOf) {
		this.asOf = asOf;
	}

	private Integer afterPatientId;

	private Integer limit;

	private Integer nextAfterPatientId;

	private Integer processedPatients;

	private Integer evaluatedModels;

	private Integer appliedDecisions;

	private Integer failedEvaluations;

	private Boolean hasMore;

	public Integer getAfterPatientId() {
		return afterPatientId;
	}

	public void setAfterPatientId(Integer afterPatientId) {
		this.afterPatientId = afterPatientId;
	}

	public Integer getLimit() {
		return limit;
	}

	public void setLimit(Integer limit) {
		this.limit = limit;
	}

	public Integer getNextAfterPatientId() {
		return nextAfterPatientId;
	}

	public void setNextAfterPatientId(Integer nextAfterPatientId) {
		this.nextAfterPatientId = nextAfterPatientId;
	}

	public Integer getProcessedPatients() {
		return processedPatients;
	}

	public void setProcessedPatients(Integer processedPatients) {
		this.processedPatients = processedPatients;
	}

	public Integer getEvaluatedModels() {
		return evaluatedModels;
	}

	public void setEvaluatedModels(Integer evaluatedModels) {
		this.evaluatedModels = evaluatedModels;
	}

	public Integer getAppliedDecisions() {
		return appliedDecisions;
	}

	public void setAppliedDecisions(Integer appliedDecisions) {
		this.appliedDecisions = appliedDecisions;
	}

	public Integer getFailedEvaluations() {
		return failedEvaluations;
	}

	public void setFailedEvaluations(Integer failedEvaluations) {
		this.failedEvaluations = failedEvaluations;
	}

	public Boolean getHasMore() {
		return hasMore;
	}

	public void setHasMore(Boolean hasMore) {
		this.hasMore = hasMore;
	}
}
