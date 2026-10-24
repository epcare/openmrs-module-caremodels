/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of built-in and registered data resolvers. Definitions may only name resolvers present
 * here; unknown ids fail closed at the data endpoint, never at runtime discovery.
 */
public class DataResolverRegistry {

	private final Map<String, DataResolver> resolvers = new HashMap<String, DataResolver>();

	public DataResolverRegistry() {
		register(new LatestObservationResolver());
		register(new LatestObservationResolver("historical-observations", java.time.Clock.systemUTC()));
		register(new ObservationSetResolver());
		register(new EncounterResolver());
		register(new ProgramStateResolver());
		register(new PatientSummaryResolver());
		register(new OrderResolver());
		register(new ConditionResolver());
		register(new DiagnosisResolver());
	}

	public synchronized void register(DataResolver resolver) {
		resolvers.put(resolver.getId(), resolver);
	}

	public synchronized DataResolver getResolver(String id) {
		return resolvers.get(id);
	}

	public synchronized List<String> getResolverIds() {
		List<String> ids = new ArrayList<String>(resolvers.keySet());
		Collections.sort(ids);
		return Collections.unmodifiableList(ids);
	}

    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    private final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.atomic.AtomicLong> calls = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.atomic.AtomicLong> failures = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.atomic.AtomicLong> nanos = new java.util.concurrent.ConcurrentHashMap<>();

    public void setTransactionManager(org.springframework.transaction.PlatformTransactionManager manager) { transactionManager = manager; }

    /** Synchronous transaction timeout: no detached worker or patient-context propagation. */
    public com.fasterxml.jackson.databind.JsonNode resolve(String id, Integer patientId, com.fasterxml.jackson.databind.JsonNode source, String reference) {
        DataResolver resolver = getResolver(id);
        if (resolver == null) throw new IllegalArgumentException("Unregistered resolver");
        long started = System.nanoTime();
        calls.computeIfAbsent(id, k -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
        try {
            if (transactionManager == null) return resolveValidated(resolver, patientId, source, reference);
            org.springframework.transaction.support.TransactionTemplate transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            transaction.setReadOnly(true); transaction.setTimeout(10);
            return transaction.execute(status -> resolveValidated(resolver, patientId, source, reference));
        } catch (RuntimeException e) {
            failures.computeIfAbsent(id, k -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
            throw e;
        } finally {
            nanos.computeIfAbsent(id, k -> new java.util.concurrent.atomic.AtomicLong()).addAndGet(System.nanoTime() - started);
        }
    }

    /** Aggregate diagnostics only: never patient IDs, source definitions or clinical values. */
    public java.util.Map<String, Long> getMetrics(String id) {
        java.util.Map<String, Long> result = new java.util.LinkedHashMap<>();
        result.put("calls", calls.containsKey(id) ? calls.get(id).get() : 0L);
        result.put("failures", failures.containsKey(id) ? failures.get(id).get() : 0L);
        result.put("totalNanos", nanos.containsKey(id) ? nanos.get(id).get() : 0L);
        return java.util.Collections.unmodifiableMap(result);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public synchronized void setExtensions(java.util.List<VersionedDataResolver> extensions) {
        for (VersionedDataResolver extension : extensions) {
            if (getResolver(extension.getId()) != null) throw new IllegalArgumentException("Duplicate registered resolver ID");
            if (extension.getSourceSchema() == null || extension.getResultSchema() == null || !extension.getVersion().matches("[0-9]+[.][0-9]+[.][0-9]+"))
                throw new IllegalArgumentException("Versioned source/result contracts are required");
            register(extension);
        }
    }
    private com.fasterxml.jackson.databind.JsonNode resolveValidated(DataResolver resolver, Integer patientId, com.fasterxml.jackson.databind.JsonNode source, String reference) {
        org.openmrs.module.caremodels.config.ValidationResult sourceValidation = SourceContract.validate(source, this, "$.source");
        if (!sourceValidation.isValid()) throw new IllegalArgumentException(sourceValidation.toString());
        com.fasterxml.jackson.databind.JsonNode result = resolver.resolve(patientId, source, reference);
        org.openmrs.module.caremodels.config.ValidationResult resultValidation = SourceContract.validateResult(result, resolver);
        if (!resultValidation.isValid()) throw new IllegalArgumentException(resultValidation.toString());
        return result;
    }
}
