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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Structural validation of care model JSON against the bundled Draft 2020-12 schemas (
 * {@code api/src/main/resources/schemas/}). Cross-file $refs resolve through a classpath URI
 * fetcher, so no schema file needs to be inlined.
 * <p>
 * Structural validation is the first gate; {@link SemanticValidator} performs the OpenMRS-aware
 * pass afterwards.
 */
public class JsonSchemaValidator {
	
	/** Base URI the schemas declare in $id; mapped onto the classpath schemas/ directory. */
	public static final String SCHEMA_BASE_URI = "https://epcare.openmrs.org/schemas/";
	
	public enum SchemaType {
		DATA_SOURCE("data-source.schema.json"), CLINICAL_RESULT("clinical-result.schema.json"), MODEL_DEFINITION(
		        "model-definition.schema.json"), STORAGE_ENVELOPE("storage-envelope.schema.json"), MEMBERSHIP_DATA(
		        "membership-data.schema.json");
		
		private final String resource;
		
		SchemaType(String resource) {
			this.resource = resource;
		}
		
		public String getResource() {
			return resource;
		}
	}
	
	private final ObjectMapper objectMapper;
	
	private final JsonSchemaFactory schemaFactory;
	
	private final Map<SchemaType, JsonSchema> schemaCache = new ConcurrentHashMap<SchemaType, JsonSchema>();
	
	public JsonSchemaValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.schemaFactory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012))
		        .uriFetcher(new ClasspathSchemaUriFetcher(), "https").build();
	}
	
	public JsonSchemaValidator() {
		this(new ObjectMapper());
	}
	
	/**
	 * Parses and structurally validates a JSON document against the given schema.
	 * 
	 * @return validation result; parse errors are reported as validation errors at path "$"
	 */
	public ValidationResult validate(String json, SchemaType schemaType) {
		JsonNode node;
		try {
			node = objectMapper.readTree(json);
		}
		catch (IOException e) {
			return ValidationResult.withError("$", "Not valid JSON: " + e.getMessage());
		}
		return validate(node, schemaType);
	}
	
	public ValidationResult validate(JsonNode node, SchemaType schemaType) {
		JsonSchema schema = schemaCache.computeIfAbsent(schemaType, this::loadSchema);
		ValidationResult result = ValidationResult.valid();
		for (ValidationMessage message : schema.validate(node)) {
			result.addError(message.getPath(), message.getMessage());
		}
		return result;
	}
	
	private JsonSchema loadSchema(SchemaType schemaType) {
		URI uri = URI.create(SCHEMA_BASE_URI + schemaType.getResource());
		return schemaFactory.getSchema(uri);
	}
	
	/** Maps {@link #SCHEMA_BASE_URI} onto classpath resources under /schemas/. */
	private class ClasspathSchemaUriFetcher implements com.networknt.schema.uri.URIFetcher {
		
		@Override
		public InputStream fetch(URI uri) throws IOException {
			String uriString = uri.toString();
			if (uriString.startsWith(SCHEMA_BASE_URI)) {
				String resource = "/schemas/" + uriString.substring(SCHEMA_BASE_URI.length());
				InputStream stream = JsonSchemaValidator.class.getResourceAsStream(resource);
				if (stream != null) {
					return stream;
				}
				throw new IOException("Schema resource not found on classpath: " + resource);
			}
			return uri.toURL().openStream();
		}
	}
}
