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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The enum lists inside the JSON Schemas and the {@link Registry} constants must stay in sync: a
 * widget/action/data-source type registered in Java but not permitted by the schema (or vice versa)
 * would silently break the contract in one of the two validation layers.
 */
public class RegistrySchemaSyncTest {
	
	private ObjectMapper mapper;
	
	private JsonNode modelDefinitionSchema;
	
	@Before
	public void setup() throws Exception {
		mapper = new ObjectMapper();
		modelDefinitionSchema = mapper.readTree(getClass().getResourceAsStream("/schemas/model-definition.schema.json"));
	}
	
	private List<String> enumOf(JsonNode schemaProperty) {
		List<String> values = new ArrayList<String>();
		for (JsonNode value : schemaProperty.path("enum")) {
			values.add(value.asText());
		}
		return values;
	}
	
	private JsonNode widgetSchema() {
		// workspace -> properties -> tabs -> items -> properties -> widgets -> items -> properties -> type
		return modelDefinitionSchema.path("properties").path("workspace").path("properties").path("tabs").path("items")
		        .path("properties").path("widgets").path("items").path("properties").path("type");
	}
	
	private JsonNode actionSchema() throws Exception {
		return mapper.readTree(getClass().getResourceAsStream("/schemas/action.schema.json")).path("properties")
		        .path("type");
	}
	
	private JsonNode dataSourceSchema() throws Exception {
		return mapper.readTree(getClass().getResourceAsStream("/schemas/data-source.schema.json")).path("properties")
		        .path("type");
	}
	
	@Test
	public void widgetTypesShouldMatchRegistry() throws Exception {
		assertEquals(new ArrayList<String>(Registry.WIDGET_TYPES), enumOf(widgetSchema()));
	}
	
	@Test
	public void actionTypesShouldMatchRegistry() throws Exception {
		assertEquals(new ArrayList<String>(Registry.ACTION_TYPES), enumOf(actionSchema()));
	}
	
	@Test
	public void dataSourceTypesShouldMatchRegistry() throws Exception {
		assertEquals(new ArrayList<String>(Registry.DATA_SOURCE_TYPES), enumOf(dataSourceSchema()));
	}
	
	@Test
	public void resultTypesShouldMatchRegistry() throws Exception {
		JsonNode resultType = mapper.readTree(getClass().getResourceAsStream("/schemas/data-source.schema.json"))
		        .path("properties").path("resultType");
		assertEquals(new ArrayList<String>(Registry.RESULT_TYPES), enumOf(resultType));
	}
}
