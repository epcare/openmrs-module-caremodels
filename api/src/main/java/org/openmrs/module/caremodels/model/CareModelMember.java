/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.model;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Version;

import org.openmrs.BaseOpenmrsData;

/**
 * One enrollment episode of a patient in a Care Model. A patient may be ACTIVE in multiple models
 * and may have multiple historical episodes in the same model: one row per episode.
 * <p>
 * Only {@link MemberStatus#ACTIVE} non-voided episodes appear as patient tabs. No location is
 * stored: each facility runs its own OpenMRS instance, so there is no facility scoping on
 * membership. Clinical content is never duplicated here; {@code membership_data} carries only
 * enrollment provenance, pathway position and transition history.
 */
@Entity
@Table(name = "care_model_member", indexes = { @Index(name = "idx_care_model_member_uuid", columnList = "uuid"),
        @Index(name = "idx_care_model_member_patient", columnList = "patient_id"),
        @Index(name = "idx_care_model_member_model", columnList = "care_model_id"),
        @Index(name = "idx_care_model_member_voided", columnList = "voided") })
public class CareModelMember extends BaseOpenmrsData implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "care_model_member_id")
	private Integer careModelMemberId;
	
	@Column(name = "care_model_id", nullable = false)
	private Integer careModelId;
	
	@Column(name = "patient_id", nullable = false)
	private Integer patientId;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 30, nullable = false)
	private MemberStatus status = MemberStatus.PENDING;
	
	/** 1-based episode counter within (patient, model); historical episodes keep their numbers. */
	@Column(name = "episode_number", nullable = false)
	private Integer episodeNumber = 1;
	
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "date_enrolled")
	private Date dateEnrolled;
	
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "date_completed")
	private Date dateCompleted;
	
	/** AUTO, CONFIRMATION, MANUAL; distinguishes how the episode came to exist. */
	@Column(name = "enrollment_source", length = 30)
	private String enrollmentSource;
	
	/**
	 * UUID of the originating clinical event/program/encounter, when the enrollment was
	 * rule-driven.
	 */
	@Column(name = "source_uuid", length = 38)
	private String sourceUuid;
	
	/**
	 * Patient-specific JSON: enrollment provenance (rule id, definition version, trigger,
	 * confirmation), pathway stage and transition history. Bounded and minimized; never a copy of
	 * clinical observations.
	 */
	@Lob
	@Column(name = "membership_data", columnDefinition = "LONGTEXT")
	private String membershipData;
	
	/** Optimistic locking on membership transitions. */
	@Version
	@Column(name = "version")
	private Integer version;
	
	// ========================
	// Enums
	// ========================
	
	/**
	 * Episode lifecycle. Only ACTIVE non-voided members are patient tabs; SUSPENDED/COMPLETED/
	 * WITHDRAWN are history; DECLINED and ENTERED_IN_ERROR remain as auditable records.
	 */
	public enum MemberStatus {
		PENDING, ACTIVE, SUSPENDED, COMPLETED, WITHDRAWN, DECLINED, ENTERED_IN_ERROR
	}
	
	// ========================
	// ID mapping (OpenMRS)
	// ========================
	
	@Override
	public Integer getId() {
		return careModelMemberId;
	}
	
	@Override
	public void setId(Integer id) {
		this.careModelMemberId = id;
	}
	
	// ========================
	// Getters and setters
	// ========================
	
	public Integer getCareModelId() {
		return careModelId;
	}
	
	public void setCareModelId(Integer careModelId) {
		this.careModelId = careModelId;
	}
	
	public Integer getPatientId() {
		return patientId;
	}
	
	public void setPatientId(Integer patientId) {
		this.patientId = patientId;
	}
	
	public MemberStatus getStatus() {
		return status;
	}
	
	public void setStatus(MemberStatus status) {
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
	
	public String getSourceUuid() {
		return sourceUuid;
	}
	
	public void setSourceUuid(String sourceUuid) {
		this.sourceUuid = sourceUuid;
	}
	
	public String getMembershipData() {
		return membershipData;
	}
	
	public void setMembershipData(String membershipData) {
		this.membershipData = membershipData;
	}
	
	public Integer getVersion() {
		return version;
	}
	
	public void setVersion(Integer version) {
		this.version = version;
	}
}
