/** This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. */
package org.openmrs.module.caremodels.rule;

import java.util.Date;

/** A resolved clinical fact, a known absence, or an unavailable/indeterminate lookup. */
public final class RuleEvidence {

	public enum Availability {
		PRESENT, ABSENT, UNKNOWN
	}

	private final Availability availability;

	private final Object value;

	private final Date effectiveTime;

	private final String unit;

	private RuleEvidence(Availability availability, Object value, Date effectiveTime, String unit) {
		this.availability = availability;
		this.value = value;
		this.effectiveTime = effectiveTime == null ? null : new Date(effectiveTime.getTime());
		this.unit = unit;
	}

	public static RuleEvidence present(Object value) {
		return present(value, null, null);
	}

	public static RuleEvidence present(Object value, Date effectiveTime, String unit) {
		return new RuleEvidence(Availability.PRESENT, value, effectiveTime, unit);
	}

	public static RuleEvidence absent() {
		return new RuleEvidence(Availability.ABSENT, null, null, null);
	}

	public static RuleEvidence unknown() {
		return new RuleEvidence(Availability.UNKNOWN, null, null, null);
	}

	public Availability getAvailability() {
		return availability;
	}

	public Object getValue() {
		return value;
	}

	public Date getEffectiveTime() {
		return effectiveTime == null ? null : new Date(effectiveTime.getTime());
	}

	public String getUnit() {
		return unit;
	}
}
