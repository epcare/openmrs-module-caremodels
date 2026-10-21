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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Canonical serialization must be deterministic: semantically equal documents with different key
 * order produce identical canonical strings and therefore identical publish checksums.
 */
public class CanonicalJsonTest {
	
	private ObjectMapper mapper;
	
	@Before
	public void setup() {
		mapper = new ObjectMapper();
	}
	
	@Test
	public void shouldBeIndependentOfKeyOrder() throws Exception {
		JsonNode a = mapper.readTree("{\"b\":1,\"a\":{\"z\":true,\"y\":[3,1,2]}}");
		JsonNode b = mapper.readTree("{\"a\":{\"y\":[3,1,2],\"z\":true},\"b\":1}");
		assertEquals(CanonicalJson.canonicalize(a), CanonicalJson.canonicalize(b));
		assertEquals(CanonicalJson.sha256(a), CanonicalJson.sha256(b));
	}
	
	@Test
	public void shouldPreserveArrayOrder() throws Exception {
		JsonNode a = mapper.readTree("[1,2,3]");
		JsonNode b = mapper.readTree("[3,2,1]");
		assertNotEquals(CanonicalJson.canonicalize(a), CanonicalJson.canonicalize(b));
	}
	
	@Test
	public void shouldProduceSortedFlatOutput() throws Exception {
		String canonical = CanonicalJson.canonicalize(mapper.readTree("{\"zz\":1,\"aa\":2,\"mm\":3}"));
		assertEquals("{\"aa\":2,\"mm\":3,\"zz\":1}", canonical);
	}
	
	@Test
	public void checksumShouldChangeWhenDefinitionChanges() throws Exception {
		JsonNode before = mapper.readTree("{\"value\":1}");
		JsonNode after = mapper.readTree("{\"value\":2}");
		assertNotEquals(CanonicalJson.sha256(before), CanonicalJson.sha256(after));
	}
	
	@Test
	public void checksumShouldBeLowercaseHexSha256() throws Exception {
		String checksum = CanonicalJson.sha256Hex("");
		assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", checksum);
	}
}
