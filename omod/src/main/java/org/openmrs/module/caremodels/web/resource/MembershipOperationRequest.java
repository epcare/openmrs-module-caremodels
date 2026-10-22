/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.web.resource;

import java.util.Date;

/** Request and response fields for clinician-initiated membership operations. */
public class MembershipOperationRequest {

	private String operation;

	private String patient;

	private String model;

	private String memberUuid;

	private String reason;

	private String transition;

	private String status;

	private Integer episodeNumber;

	private Date dateEnrolled;

	private Date dateCompleted;

	private String enrollmentSource;

	public String getOperation() {
		return operation;
	}

	public void setOperation(String operation) {
		this.operation = operation;
	}

	public String getPatient() {
		return patient;
	}

	public void setPatient(String patient) {
		this.patient = patient;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getMemberUuid() {
		return memberUuid;
	}

	public void setMemberUuid(String memberUuid) {
		this.memberUuid = memberUuid;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getTransition() {
		return transition;
	}

	public void setTransition(String transition) {
		this.transition = transition;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getEpisodeNumber() {
		return episodeNumber;
	}

	public void setEpisodeNumber(Integer episodeNumber) {
		this.episodeNumber = episodeNumber;
	}

	public Date getDateEnrolled() {
		return dateEnrolled;
	}

	public void setDateEnrolled(Date dateEnrolled) {
		this.dateEnrolled = dateEnrolled;
	}

	public Date getDateCompleted() {
		return dateCompleted;
	}

	public void setDateCompleted(Date dateCompleted) {
		this.dateCompleted = dateCompleted;
	}

	public String getEnrollmentSource() {
		return enrollmentSource;
	}

	public void setEnrollmentSource(String enrollmentSource) {
		this.enrollmentSource = enrollmentSource;
	}
}
