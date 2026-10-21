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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Envelope transitions: draft save and publish with optimistic compare-and-swap on revision;
 * bounded history; checksum reflects the published definition.
 */
public class StorageEnvelopeCasTest {
	
	private ObjectMapper mapper;
	
	@Before
	public void setup() {
		mapper = new ObjectMapper();
	}
	
	private ObjectNode definition(String label) {
		ObjectNode node = mapper.createObjectNode();
		node.put("label", label);
		return node;
	}
	
	@Test
	public void saveDraftShouldBumpRevision() {
		StorageEnvelope envelope = new StorageEnvelope();
		assertEquals(Integer.valueOf(0), envelope.getRevision());
		
		envelope.saveDraft(definition("draft 1"), 0);
		assertEquals(Integer.valueOf(1), envelope.getRevision());
		assertNotNull(envelope.getDraft());
		assertEquals("0.0.0", envelope.getDraft().getBasedOnVersion());
	}
	
	@Test(expected = StaleRevisionException.class)
	public void saveDraftShouldRejectStaleRevision() {
		StorageEnvelope envelope = new StorageEnvelope();
		envelope.saveDraft(definition("draft 1"), 0);
		
		// a second writer still believes the revision is 0
		envelope.saveDraft(definition("stale draft"), 0);
	}
	
	@Test
	public void publishShouldChecksumDefinitionAndArchiveHistory() {
		StorageEnvelope envelope = new StorageEnvelope();
		envelope.saveDraft(definition("v1 content"), 0);
		envelope.publishDraft("1.0.0", 1, null);
		
		assertNotNull(envelope.getPublished());
		assertEquals("1.0.0", envelope.getPublished().getVersion());
		assertEquals(CanonicalJson.sha256(definition("v1 content")), envelope.getPublished().getChecksum());
		assertEquals(0, envelope.getHistory().size());
		
		// second publication archives the first snapshot
		envelope.saveDraft(definition("v2 content"), 2);
		envelope.publishDraft("1.1.0", 3, null);
		assertEquals(1, envelope.getHistory().size());
		assertEquals("1.0.0", envelope.getHistory().get(0).getVersion());
	}
	
	@Test(expected = StaleRevisionException.class)
	public void publishShouldRejectStaleRevision() {
		StorageEnvelope envelope = new StorageEnvelope();
		envelope.saveDraft(definition("draft"), 0);
		
		// concurrent publisher wins the race; this writer's publish is stale
		envelope.saveDraft(definition("concurrent draft"), 1);
		envelope.publishDraft("1.0.0", 1, null);
	}
	
	@Test
	public void historyShouldStayBounded() {
		StorageEnvelope envelope = new StorageEnvelope();
		int revisions = 0;
		for (int i = 0; i < 25; i++) {
			envelope.saveDraft(definition("content " + i), revisions++);
			envelope.publishDraft("1." + i + ".0", revisions++, null);
		}
		assertEquals(StorageEnvelope.MAX_HISTORY, envelope.getHistory().size());
	}
	
	@Test(expected = IllegalStateException.class)
	public void publishShouldRequireADraft() {
		new StorageEnvelope().publishDraft("1.0.0", 0, null);
	}
}
