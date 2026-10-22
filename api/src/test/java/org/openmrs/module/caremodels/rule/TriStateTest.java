/** This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. */
package org.openmrs.module.caremodels.rule;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TriStateTest {

	@Test
	public void shouldApplyStrongKleeneTruthTables() {
		assertEquals(TriState.FALSE, TriState.UNKNOWN.and(TriState.FALSE));
		assertEquals(TriState.UNKNOWN, TriState.UNKNOWN.and(TriState.TRUE));
		assertEquals(TriState.TRUE, TriState.UNKNOWN.or(TriState.TRUE));
		assertEquals(TriState.UNKNOWN, TriState.UNKNOWN.or(TriState.FALSE));
		assertEquals(TriState.UNKNOWN, TriState.UNKNOWN.not());
		assertEquals(TriState.FALSE, TriState.TRUE.not());
	}
}
