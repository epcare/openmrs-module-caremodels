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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Loads contract fixtures from api/src/test/resources for the W0 validator tests.
 */
final class Fixtures {
	
	private Fixtures() {
	}
	
	static String sample(String name) throws Exception {
		return read("/samples/" + name + ".json");
	}
	
	static String negative(String name) throws Exception {
		return read("/negative/" + name + ".json");
	}
	
	private static String read(String path) throws Exception {
		InputStream stream = Fixtures.class.getResourceAsStream(path);
		if (stream == null) {
			throw new IllegalStateException("Fixture not found: " + path);
		}
		StringBuilder sb = new StringBuilder();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}
		}
		finally {
			stream.close();
		}
		return sb.toString();
	}
	
	static void assertValid(ValidationResult result, String context) {
		assertTrue(context + " should be valid but was: " + result, result.isValid());
	}
	
	static void assertInvalid(ValidationResult result, String context) {
		assertFalse(context + " should be invalid", result.isValid());
	}
}
