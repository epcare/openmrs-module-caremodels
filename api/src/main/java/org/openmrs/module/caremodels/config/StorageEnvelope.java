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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Typed view of the storage envelope persisted in {@code care_model.configuration}: one editable
 * draft, one published snapshot, bounded history. Pure DTO + transition helpers; persistence and
 * transaction boundaries live in the service layer (W2).
 * <p>
 * Published snapshots are immutable at the API level; publishing archives the previous published
 * snapshot into history. The underlying JSON column is a single mutable field, which is why
 * immutability is documented as an API-level guarantee rather than physical row immutability.
 */
public class StorageEnvelope {
	
	public static final String STORAGE_SCHEMA_VERSION = "1.0.0";
	
	/** Maximum number of archived published snapshots kept in history. */
	public static final int MAX_HISTORY = 10;
	
	private String storageSchemaVersion = STORAGE_SCHEMA_VERSION;
	
	private Integer revision = 0;
	
	private Draft draft;
	
	private PublishedSnapshot published;
	
	private List<PublishedSnapshot> history = new ArrayList<PublishedSnapshot>();
	
	public static class Draft {
		
		private String basedOnVersion;
		
		private JsonNode definition;
		
		public String getBasedOnVersion() {
			return basedOnVersion;
		}
		
		public void setBasedOnVersion(String basedOnVersion) {
			this.basedOnVersion = basedOnVersion;
		}
		
		public JsonNode getDefinition() {
			return definition;
		}
		
		public void setDefinition(JsonNode definition) {
			this.definition = definition;
		}
	}
	
	public static class PublishedSnapshot {
		
		private String version;
		
		private String checksum;
		
		private Date publishedAt;
		
		private JsonNode definition;
		
		public String getVersion() {
			return version;
		}
		
		public void setVersion(String version) {
			this.version = version;
		}
		
		public String getChecksum() {
			return checksum;
		}
		
		public void setChecksum(String checksum) {
			this.checksum = checksum;
		}
		
		public Date getPublishedAt() {
			return publishedAt;
		}
		
		public void setPublishedAt(Date publishedAt) {
			this.publishedAt = publishedAt;
		}
		
		public JsonNode getDefinition() {
			return definition;
		}
		
		public void setDefinition(JsonNode definition) {
			this.definition = definition;
		}
	}
	
	// ========================
	// Transitions (compare-and-swap on revision)
	// ========================
	
	/**
	 * Saves a new draft with optimistic locking. The envelope revision must match
	 * {@code expectedRevision} or the write is rejected.
	 */
	public StorageEnvelope saveDraft(JsonNode definition, Integer expectedRevision) {
		assertCurrentRevision(expectedRevision);
		Draft newDraft = new Draft();
		newDraft.setBasedOnVersion(published != null ? published.getVersion() : "0.0.0");
		newDraft.setDefinition(definition);
		this.draft = newDraft;
		this.revision = revision + 1;
		return this;
	}
	
	/**
	 * Publishes the draft as a new version: validates the expected revision, archives the previous
	 * published snapshot into bounded history, computes the canonical checksum and bumps the
	 * revision. Single-threaded use within a transaction; the service layer supplies the locking.
	 */
	public StorageEnvelope publishDraft(String version, Integer expectedRevision, Date publishedAt) {
		if (draft == null || draft.getDefinition() == null) {
			throw new IllegalStateException("No draft to publish");
		}
		assertCurrentRevision(expectedRevision);
		
		if (published != null) {
			history.add(0, published);
			while (history.size() > MAX_HISTORY) {
				history.remove(history.size() - 1);
			}
		}
		
		PublishedSnapshot snapshot = new PublishedSnapshot();
		snapshot.setVersion(version);
		snapshot.setChecksum(CanonicalJson.sha256(draft.getDefinition()));
		snapshot.setPublishedAt(publishedAt != null ? publishedAt : new Date());
		snapshot.setDefinition(draft.getDefinition());
		this.published = snapshot;
		
		// The draft stays (based on the now-published version) so further edits have a base
		this.draft.setBasedOnVersion(version);
		this.revision = revision + 1;
		return this;
	}
	
	private void assertCurrentRevision(Integer expectedRevision) {
		if (expectedRevision == null || !expectedRevision.equals(this.revision)) {
			throw new StaleRevisionException(expectedRevision, this.revision);
		}
	}
	
	// ========================
	// Serialization helpers
	// ========================
	
	/**
	 * Parses a stored envelope. Date fields are read as ISO-8601 strings matching the schema's
	 * date-time format.
	 */
	public static StorageEnvelope fromJson(ObjectMapper mapper, String json) {
		try {
			mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
			return mapper.readValue(json, StorageEnvelope.class);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot parse storage envelope: " + e.getMessage(), e);
		}
	}
	
	public String toJson(ObjectMapper mapper) {
		try {
			// ISO-8601 date-time strings, as required by the storage envelope schema
			mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
			return mapper.writeValueAsString(this);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot serialize storage envelope", e);
		}
	}
	
	// ========================
	// Getters and setters
	// ========================
	
	public String getStorageSchemaVersion() {
		return storageSchemaVersion;
	}
	
	public void setStorageSchemaVersion(String storageSchemaVersion) {
		this.storageSchemaVersion = storageSchemaVersion;
	}
	
	public Integer getRevision() {
		return revision;
	}
	
	public void setRevision(Integer revision) {
		this.revision = revision;
	}
	
	public Draft getDraft() {
		return draft;
	}
	
	public void setDraft(Draft draft) {
		this.draft = draft;
	}
	
	public PublishedSnapshot getPublished() {
		return published;
	}
	
	public void setPublished(PublishedSnapshot published) {
		this.published = published;
	}
	
	public List<PublishedSnapshot> getHistory() {
		return history;
	}
	
	public void setHistory(List<PublishedSnapshot> history) {
		this.history = history;
	}
}
