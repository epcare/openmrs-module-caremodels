/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.config;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Bounded, thread-safe cache of parsed storage envelopes, keyed by care model id.
 * <p>
 * Parsing the configuration LONGTEXT (draft + published + up to 10 history snapshots) dominates the
 * cost of every workspace/data request and every rule-driven enrollment evaluation. Entries are
 * validated by comparing the raw configuration string, so a replaced envelope (publish, shipped
 * upgrade) can never be served stale regardless of the revision column; entries are simply
 * overwritten on change. The parsed tree is shared between threads and therefore treated as
 * strictly read-only by every caller — mutation paths ({@code CareModelServiceImpl}) parse fresh
 * instead of using this cache.
 */
public class StorageEnvelopeCache {
	
	/** Default capacity bounds memory: large definitions are tens of KB each. */
	public static final int DEFAULT_CAPACITY = 64;
	
	private final int capacity;
	
	private final LinkedHashMap<Integer, Entry> cache;
	
	private final ObjectMapper mapper = new ObjectMapper().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
	
	private static final class Entry {
		
		private final String configurationJson;
		
		private final StorageEnvelope envelope;
		
		private Entry(String configurationJson, StorageEnvelope envelope) {
			this.configurationJson = configurationJson;
			this.envelope = envelope;
		}
	}
	
	public StorageEnvelopeCache() {
		this(DEFAULT_CAPACITY);
	}
	
	public StorageEnvelopeCache(int capacity) {
		this.capacity = capacity;
		// accessOrder + removeEldestEntry = simple LRU; all access is synchronized
		this.cache = new LinkedHashMap<Integer, Entry>(
		                                               16, 0.75f, true) {
			
			@Override
			protected boolean removeEldestEntry(Map.Entry<Integer, Entry> eldest) {
				return size() > capacity;
			}
		};
	}
	
	/**
	 * Returns the parsed envelope for the given configuration when it is cached and unchanged;
	 * parses and caches otherwise.
	 * 
	 * @param modelId owning care model id (cache key)
	 * @param configurationJson the stored configuration LONGTEXT
	 * @throws IllegalStateException when the configuration is not a valid envelope, as
	 *             {@link StorageEnvelope#fromJson} does
	 */
	public StorageEnvelope get(Integer modelId, String configurationJson) {
		if (modelId == null) {
			return StorageEnvelope.fromJson(mapper, configurationJson);
		}
		synchronized (cache) {
			Entry entry = cache.get(modelId);
			if (entry != null && entry.configurationJson.equals(configurationJson)) {
				return entry.envelope;
			}
		}
		StorageEnvelope parsed = StorageEnvelope.fromJson(mapper, configurationJson);
		synchronized (cache) {
			cache.put(modelId, new Entry(configurationJson, parsed));
		}
		return parsed;
	}
	
	/** Drops one model's entry; a no-op when absent. Provided for explicitness, not required. */
	public void evict(Integer modelId) {
		if (modelId == null) {
			return;
		}
		synchronized (cache) {
			cache.remove(modelId);
		}
	}
}
