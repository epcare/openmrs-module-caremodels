/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import java.util.Date;
import java.util.Objects;

/** A committed clinical change that may trigger care-model enrollment evaluation. */
public final class ClinicalChangeEvent {
	
	public enum TriggerType {
		PROGRAM_CHANGED, ENCOUNTER_SAVED, ENCOUNTER_CHANGED, ENCOUNTER_VOIDED, OBSERVATION_SAVED, OBSERVATION_CHANGED, OBSERVATION_VOIDED
	}
	
	private final TriggerType triggerType;
	
	private final String patientUuid;
	
	private final String sourceUuid;
	
	private final String referenceUuid;
	
	private final String reviewSourceUuid;
	
	private final Date occurredAt;
	
	public ClinicalChangeEvent(TriggerType triggerType, String patientUuid, String sourceUuid, String referenceUuid,
	    Date occurredAt) {
		this(triggerType, patientUuid, sourceUuid, referenceUuid, occurredAt, null);
	}
	
	public ClinicalChangeEvent(TriggerType triggerType, String patientUuid, String sourceUuid, String referenceUuid,
	    Date occurredAt, String reviewSourceUuid) {
		this.triggerType = Objects.requireNonNull(triggerType, "triggerType");
		this.patientUuid = Objects.requireNonNull(patientUuid, "patientUuid");
		this.sourceUuid = Objects.requireNonNull(sourceUuid, "sourceUuid");
		this.referenceUuid = Objects.requireNonNull(referenceUuid, "referenceUuid");
		this.occurredAt = new Date(Objects.requireNonNull(occurredAt, "occurredAt").getTime());
		this.reviewSourceUuid = reviewSourceUuid;
	}
	
	public TriggerType getTriggerType() {
		return triggerType;
	}
	
	public String getPatientUuid() {
		return patientUuid;
	}
	
	public String getSourceUuid() {
		return sourceUuid;
	}
	
	/** Returns the program, encounter-type, or concept UUID used for trigger filtering. */
	public String getReferenceUuid() {
		return referenceUuid;
	}
	
	public Date getOccurredAt() {
		return new Date(occurredAt.getTime());
	}
	
	/** The prior or voided evidence UUID whose rule-driven memberships require human review. */
	public String getReviewSourceUuid() {
		return reviewSourceUuid;
	}
}
