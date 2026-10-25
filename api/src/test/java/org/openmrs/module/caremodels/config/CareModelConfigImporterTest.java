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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.openmrs.module.caremodels.api.CareModelService;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Folder-scanning behavior of the startup import: pack subdirectories are traversed, valid wrappers
 * reach the service with their identity fields, a broken file never blocks its neighbours, and the
 * version gate's null return is surfaced as a skip.
 */
public class CareModelConfigImporterTest {
	
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private CareModelService service;
	
	private CareModelConfigImporter importer;
	
	@Before
	public void setup() {
		service = mock(CareModelService.class);
		importer = new CareModelConfigImporter(service);
	}
	
	@Test
	public void shouldImportEveryJsonFileAcrossPacks() throws Exception {
		File hiv = temporaryFolder.newFolder("configuration", "caremodel", "hiv");
		File tb = temporaryFolder.newFolder("configuration", "caremodel", "tb");
		writeWrapper(hiv, "hiv-care.json", "HIVCT");
		writeWrapper(tb, "tb-care.json", "TBCT");
		
		importer.importShippedModels(new File(temporaryFolder.getRoot(), "configuration/caremodel"));
		
		verify(service).importShippedModel(eq("HIVCT"), anyString(), anyString(), anyString());
		verify(service).importShippedModel(eq("TBCT"), anyString(), anyString(), anyString());
	}
	
	@Test
	public void shouldPassWrapperFieldsToTheService() throws Exception {
		File pack = temporaryFolder.newFolder("configuration", "caremodel", "hiv");
		ObjectNode wrapper = wrapperBody("HIVCT");
		wrapper.put("name", "HIV Care & Treatment");
		wrapper.put("privilege", "Care Models: View HIV Care");
		File file = write(pack, "hiv-care.json", wrapper);
		
		importer.importShippedFile(file);
		
		verify(service).importShippedModel(eq("HIVCT"), eq("HIV Care & Treatment"), eq("Care Models: View HIV Care"),
		    anyString());
	}
	
	@Test
	public void brokenFileShouldNotBlockItsNeighbours() throws Exception {
		File pack = temporaryFolder.newFolder("configuration", "caremodel", "hiv");
		write(pack, "broken.json", mapper.createObjectNode().put("no", "envelope"));
		writeWrapper(pack, "good.json", "HIVCT");
		
		importer.importShippedModels(new File(temporaryFolder.getRoot(), "configuration/caremodel"));
		
		verify(service).importShippedModel(eq("HIVCT"), anyString(), anyString(), anyString());
	}
	
	@Test
	public void wrapperMissingIdentityFieldsShouldBeRejected() throws Exception {
		File pack = temporaryFolder.newFolder("configuration", "caremodel", "hiv");
		ObjectNode wrapper = wrapperBody(null);
		wrapper.put("name", "n");
		wrapper.put("privilege", "p");
		File file = write(pack, "hiv-care.json", wrapper);
		
		try {
			importer.importShippedFile(file);
		}
		catch (IllegalArgumentException expected) {}
		verifyNoInteractions(service);
	}
	
	@Test
	public void missingConfigurationDirectoryShouldBeANoOp() {
		importer.importShippedModels(new File(temporaryFolder.getRoot(), "does-not-exist"));
		
		verifyNoInteractions(service);
	}
	
	@Test
	public void versionGateSkipShouldSurfaceAsNull() throws Exception {
		File pack = temporaryFolder.newFolder("configuration", "caremodel", "hiv");
		File file = writeWrapper(pack, "hiv-care.json", "HIVCT");
		when(service.importShippedModel(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
		
		assertNull(importer.importShippedFile(file));
	}
	
	private ObjectNode wrapperBody(String code) {
		ObjectNode wrapper = mapper.createObjectNode();
		if (code != null) {
			wrapper.put("code", code);
		}
		wrapper.put("name", "Model " + code);
		wrapper.put("privilege", "Care Models: View");
		ObjectNode envelope = wrapper.putObject("envelope");
		envelope.put("storageSchemaVersion", StorageEnvelope.STORAGE_SCHEMA_VERSION);
		envelope.put("revision", 1);
		ObjectNode published = envelope.putObject("published");
		published.put("version", "1.0.0");
		published.put("checksum", "unused-by-the-importer");
		published.putObject("definition");
		return wrapper;
	}
	
	private File writeWrapper(File pack, String fileName, String code) throws IOException {
		return write(pack, fileName, wrapperBody(code));
	}
	
	private File write(File pack, String fileName, ObjectNode body) throws IOException {
		File file = new File(pack, fileName);
		Files.write(file.toPath(), body.toString().getBytes(StandardCharsets.UTF_8));
		return file;
	}
}
