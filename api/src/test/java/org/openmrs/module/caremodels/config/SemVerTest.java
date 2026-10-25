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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SemVerTest {
	
	@Test
	public void shouldOrderBySegment() {
		assertEquals(-1, sign(SemVer.compare("1.0.0", "1.0.1")));
		assertEquals(-1, sign(SemVer.compare("1.9.9", "1.10.0")));
		assertEquals(-1, sign(SemVer.compare("1.2.3", "2.0.0")));
		assertEquals(0, sign(SemVer.compare("3.4.5", "3.4.5")));
		assertEquals(1, sign(SemVer.compare("2.10.0", "2.9.0")));
		assertEquals(-1, sign(SemVer.compare("0.1.0", "0.1.1")));
	}
	
	@Test
	public void shouldIgnoreSurroundingWhitespace() {
		assertEquals(0, sign(SemVer.compare(" 1.2.3 ", "1.2.3")));
	}
	
	@Test
	public void shouldRejectMalformedVersions() {
		assertNull(SemVer.parse("1.2"));
		assertNull(SemVer.parse("1.2.3.4"));
		assertNull(SemVer.parse("1.2.x"));
		assertNull(SemVer.parse("v1.2.3"));
		assertNull(SemVer.parse(null));
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void compareShouldFailFastOnMalformedInput() {
		SemVer.compare("1.2", "1.2.3");
	}
	
	private static int sign(int value) {
		return Integer.signum(value);
	}
}
