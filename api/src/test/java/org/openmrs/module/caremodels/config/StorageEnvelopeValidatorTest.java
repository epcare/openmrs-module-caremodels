/** This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. */
package org.openmrs.module.caremodels.config;

import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.caremodels.config.StorageEnvelope.Draft;
import org.openmrs.module.caremodels.config.StorageEnvelope.PublishedSnapshot;

public class StorageEnvelopeValidatorTest {
	
	private JsonNode definition;
	
	@Before
	public void setup() throws Exception {
		definition = new ObjectMapper().readTree(Fixtures.sample("minimal-model"));
	}
	
	@Test
	public void shouldAcceptConsistentEnvelope() {
		Fixtures.assertValid(StorageEnvelopeValidator.validate(envelope("1.0.0", CanonicalJson.sha256(definition))),
		    "consistent envelope");
	}
	
	@Test
	public void shouldRejectTamperedChecksum() {
		Fixtures.assertInvalid(StorageEnvelopeValidator.validate(envelope("1.0.0", repeat("0", 64))), "tampered checksum");
	}
	
	@Test
	public void shouldRejectDuplicatePublishedVersions() {
		StorageEnvelope envelope = envelope("1.0.0", CanonicalJson.sha256(definition));
		envelope.setHistory(Arrays.asList(snapshot("1.0.0", CanonicalJson.sha256(definition))));
		Fixtures.assertInvalid(StorageEnvelopeValidator.validate(envelope), "duplicate snapshot version");
	}
	
	@Test
	public void shouldRejectRevisionThatPredatesPublishedState() {
		StorageEnvelope envelope = envelope("1.0.0", CanonicalJson.sha256(definition));
		envelope.setRevision(1);
		Fixtures.assertInvalid(StorageEnvelopeValidator.validate(envelope), "inconsistent revision");
	}
	
	private StorageEnvelope envelope(String version, String checksum) {
		StorageEnvelope envelope = new StorageEnvelope();
		envelope.setRevision(2);
		envelope.setPublished(snapshot(version, checksum));
		Draft draft = new Draft();
		draft.setBasedOnVersion(version);
		draft.setDefinition(definition);
		envelope.setDraft(draft);
		return envelope;
	}
	
	private PublishedSnapshot snapshot(String version, String checksum) {
		PublishedSnapshot snapshot = new PublishedSnapshot();
		snapshot.setVersion(version);
		snapshot.setChecksum(checksum);
		snapshot.setDefinition(definition);
		return snapshot;
	}
	
	private String repeat(String value, int count) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < count; i++) {
			result.append(value);
		}
		return result.toString();
	}
}
