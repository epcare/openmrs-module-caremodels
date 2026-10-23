/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.dto;

/** Result and continuation cursor for one bounded reconciliation batch. */
public class EnrollmentReconciliationResult {
	
	private Integer nextAfterPatientId;
	
	private int processedPatients;
	
	private int evaluatedModels;
	
	private int appliedDecisions;
	
	private int failedEvaluations;
	
	private boolean hasMore;
	
	public Integer getNextAfterPatientId() {
		return nextAfterPatientId;
	}
	
	public void setNextAfterPatientId(Integer nextAfterPatientId) {
		this.nextAfterPatientId = nextAfterPatientId;
	}
	
	public int getProcessedPatients() {
		return processedPatients;
	}
	
	public void setProcessedPatients(int processedPatients) {
		this.processedPatients = processedPatients;
	}
	
	public int getEvaluatedModels() {
		return evaluatedModels;
	}
	
	public void setEvaluatedModels(int evaluatedModels) {
		this.evaluatedModels = evaluatedModels;
	}
	
	public int getAppliedDecisions() {
		return appliedDecisions;
	}
	
	public void setAppliedDecisions(int appliedDecisions) {
		this.appliedDecisions = appliedDecisions;
	}
	
	public int getFailedEvaluations() {
		return failedEvaluations;
	}
	
	public void setFailedEvaluations(int failedEvaluations) {
		this.failedEvaluations = failedEvaluations;
	}
	
	public boolean isHasMore() {
		return hasMore;
	}
	
	public void setHasMore(boolean hasMore) {
		this.hasMore = hasMore;
	}
}
