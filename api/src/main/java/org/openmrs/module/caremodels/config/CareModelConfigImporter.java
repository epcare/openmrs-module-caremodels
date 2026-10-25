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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.CareModelService;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Startup bootstrap for shipped care models. Scans {@code app data
 * dir}/configuration/caremodel/<content pack>/*.json}, where each file wraps a storage envelope
 * with its model identity:
 * 
 * <pre>
 * {
 *   "code": "HIVCT",
 *   "name": "HIV Care &amp; Treatment",
 *   "privilege": "Care Models: View HIV Care",
 *   "envelope": { ...storage envelope... }
 * }
 * </pre>
 * 
 * Content packs ship their files under their own subdirectory (hiv, tb, epcare, ...), matching the
 * Initializer convention of {@code configuration/<package>/...}. Imports are version-gated by
 * {@link CareModelService#importShippedModel}: never created twice, never downgraded, never
 * overwriting internally published improvements. One broken file is logged and skipped — it cannot
 * block the rest of the configuration or module startup.
 */
public class CareModelConfigImporter {
	
	public static final String CONFIG_DIR_NAME = "caremodel";
	
	private static final Logger log = LoggerFactory.getLogger(CareModelConfigImporter.class);
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	private final CareModelService service;
	
	public CareModelConfigImporter(CareModelService service) {
		this.service = service;
	}
	
	/** The conventional configuration root; may not exist on a fresh installation. */
	public static File resolveConfigurationRoot() {
		String appDataDir = OpenmrsUtil.getApplicationDataDirectory();
		if (appDataDir == null || appDataDir.trim().isEmpty()) {
			return null;
		}
		return new File(appDataDir, "configuration/" + CONFIG_DIR_NAME);
	}
	
	/**
	 * Runs at startup: imports every shipped model found under the conventional configuration root.
	 */
	public void importShippedModels() {
		importShippedModels(resolveConfigurationRoot());
	}
	
	/** Imports every shipped model under the given root; a missing directory is a no-op. */
	void importShippedModels(File root) {
		if (root == null || !root.isDirectory()) {
			log.info("No care model configuration directory at {}; nothing to import",
			    root == null ? "the application data directory" : root.getAbsolutePath());
			return;
		}
		int imported = 0;
		int skipped = 0;
		int failed = 0;
		for (File pack : listDirectories(root)) {
			for (File file : listJsonFiles(pack)) {
				try {
					if (importShippedFile(file) != null) {
						imported++;
					} else {
						skipped++;
					}
				}
				catch (Exception e) {
					failed++;
					log.error("Failed to import care model configuration from {}", file.getAbsolutePath(), e);
				}
			}
		}
		log.info("Care model configuration import complete: {} imported, {} skipped, {} failed", imported, skipped, failed);
	}
	
	/** Imports one wrapper file; returns the saved model, or null when the version gate skipped it. */
	public org.openmrs.module.caremodels.model.CareModel importShippedFile(File file) throws IOException {
		JsonNode wrapper = objectMapper.readTree(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		String code = requiredText(wrapper, "code", file);
		String name = requiredText(wrapper, "name", file);
		String privilege = requiredText(wrapper, "privilege", file);
		JsonNode envelope = wrapper.path("envelope");
		if (!envelope.isObject()) {
			throw new IllegalArgumentException("'" + file.getName() + "' must carry an envelope object");
		}
		return service.importShippedModel(code, name, privilege, envelope.toString());
	}
	
	private static String requiredText(JsonNode wrapper, String field, File file) {
		JsonNode value = wrapper.path(field);
		if (!value.isTextual() || value.asText().trim().isEmpty()) {
			throw new IllegalArgumentException("'" + file.getName() + "' must declare a non-empty \"" + field + "\"");
		}
		return value.asText().trim();
	}
	
	private static File[] listDirectories(File root) {
		File[] packs = root.listFiles(File::isDirectory);
		return packs == null ? new File[0] : packs;
	}
	
	private static File[] listJsonFiles(File pack) {
		File[] files = pack.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
		return files == null ? new File[0] : files;
	}
	
	/** Convenience accessor for callers with an authenticated context. */
	public static CareModelConfigImporter fromContext() {
		return new CareModelConfigImporter(Context.getService(CareModelService.class));
	}
}
