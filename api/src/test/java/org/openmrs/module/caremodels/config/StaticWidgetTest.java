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
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaticWidgetTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private final JsonSchemaValidator validator = new JsonSchemaValidator();

	private ObjectNode definition(String type, String properties) throws Exception {
		ObjectNode definition = (ObjectNode) mapper.readTree(Fixtures.sample("minimal-model"));
		ArrayNode widgets = (ArrayNode) definition.path("workspace").path("tabs").get(0).path("widgets");
		widgets.removeAll();
		ObjectNode widget = widgets.addObject().put("id", "static").put("type", type);
		widget.set("properties", mapper.readTree(properties));
		return definition;
	}

	private boolean valid(ObjectNode definition) {
		return validator.validate(definition, JsonSchemaValidator.SchemaType.MODEL_DEFINITION).isValid();
	}

	@Test
	public void shouldAcceptStaticWidgetsWithoutSources() throws Exception {
		assertTrue(valid(definition("text-note", "{\"text\":\"Plain note\"}")));
		assertTrue(valid(definition("section-heading", "{\"text\":\"History\",\"level\":2}")));
		assertTrue(valid(definition("divider", "{}")));
		assertTrue(valid(definition("spacer", "{\"size\":\"small\"}")));
		assertTrue(valid(definition("static-callout", "{\"text\":\"Static guidance\",\"tone\":\"info\"}")));
	}

	@Test
	public void shouldRejectExecutableUnknownAndInvalidProperties() throws Exception {
		assertFalse(valid(definition("text-note", "{\"text\":\"Note\",\"onClick\":\"execute\"}")));
		assertFalse(valid(definition("section-heading", "{\"text\":\"Heading\",\"level\":1}")));
		assertFalse(valid(definition("spacer", "{\"size\":999}")));
		assertFalse(valid(definition("static-callout", "{\"text\":\"Guidance\",\"tone\":\"clinical-critical\"}")));
		ObjectNode definition = definition("divider", "{}");
		((ObjectNode) definition.path("workspace").path("tabs").get(0).path("widgets").get(0)).put("dataSource", "secret");
		assertFalse(valid(definition));
	}

	@Test
	public void shouldPreserveAllExistingFixtures() throws Exception {
		for (String name : new String[] { "minimal-model", "hiv-model", "ncd-model", "anc-model" }) {
			ValidationResult result = validator.validate(Fixtures.sample(name),
			    JsonSchemaValidator.SchemaType.MODEL_DEFINITION);
			assertTrue(name + result.toString(), result.isValid());
		}
	}
}
