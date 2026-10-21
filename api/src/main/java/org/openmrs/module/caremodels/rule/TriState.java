/** This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. */
package org.openmrs.module.caremodels.rule;

/** Three-valued logic used when clinical evidence is incomplete or unavailable. */
public enum TriState {
	TRUE, FALSE, UNKNOWN;

	public TriState not() {
		return this == TRUE ? FALSE : this == FALSE ? TRUE : UNKNOWN;
	}

	public TriState and(TriState other) {
		if (this == FALSE || other == FALSE) {
			return FALSE;
		}
		return this == TRUE && other == TRUE ? TRUE : UNKNOWN;
	}

	public TriState or(TriState other) {
		if (this == TRUE || other == TRUE) {
			return TRUE;
		}
		return this == FALSE && other == FALSE ? FALSE : UNKNOWN;
	}
}
