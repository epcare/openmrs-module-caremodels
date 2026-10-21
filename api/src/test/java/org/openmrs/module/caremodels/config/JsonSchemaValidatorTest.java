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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Structural validation of the bundled sample and negative fixtures. The negative fixtures
 * implement acceptance criterion 12 (JSON with unregistered widget, unresolved concept, invalid
 * form or unauthorized operation cannot publish) at the structural layer; the semantic layer is
 * covered by {@link SemanticValidatorTest}.
 */
public class JsonSchemaValidatorTest {
	
	private JsonSchemaValidator validator;
	
	@Before
	public void setup() {
		validator = new JsonSchemaValidator();
	}
	
	@Test
	public void shouldValidateMinimalModel() throws Exception {
		Fixtures.assertValid(
		    validator.validate(Fixtures.sample("minimal-model"), JsonSchemaValidator.SchemaType.MODEL_DEFINITION),
		    "minimal model");
	}
	
	@Test
	public void shouldValidateHivModel() throws Exception {
		Fixtures.assertValid(
		    validator.validate(Fixtures.sample("hiv-model"), JsonSchemaValidator.SchemaType.MODEL_DEFINITION), "HIV model");
	}
	
	@Test
	public void shouldValidateAncModel() throws Exception {
		Fixtures.assertValid(
		    validator.validate(Fixtures.sample("anc-model"), JsonSchemaValidator.SchemaType.MODEL_DEFINITION), "ANC model");
	}
	
	@Test
	public void shouldValidateNcdModelWithNestedRules() throws Exception {
		Fixtures.assertValid(
		    validator.validate(Fixtures.sample("ncd-model"), JsonSchemaValidator.SchemaType.MODEL_DEFINITION), "NCD model");
	}
	
	@Test
	public void shouldRejectPrivilegeClaimInJson() throws Exception {
		Fixtures.assertInvalid(
		    validator.validate(Fixtures.negative("privilege-in-json"), JsonSchemaValidator.SchemaType.MODEL_DEFINITION),
		    "privilege claim inside JSON");
	}
	
	@Test
	public void shouldRejectFacilityOverlayKeys() throws Exception {
		Fixtures.assertInvalid(
		    validator.validate(Fixtures.negative("facilities-overlay"), JsonSchemaValidator.SchemaType.MODEL_DEFINITION),
		    "facility overlay keys");
	}
	
	@Test
	public void shouldRejectExecutableSqlInDataSource() throws Exception {
		Fixtures.assertInvalid(validator.validate(Fixtures.negative("executable-sql-in-datasource"),
		    JsonSchemaValidator.SchemaType.MODEL_DEFINITION), "executable SQL in data source");
	}
	
	@Test
	public void shouldRejectUnregisteredWidgetType() throws Exception {
		Fixtures.assertInvalid(
		    validator.validate(Fixtures.negative("unregistered-widget"), JsonSchemaValidator.SchemaType.MODEL_DEFINITION),
		    "unregistered widget type");
	}
	
	@Test
	public void shouldRejectInvalidFormUuid() throws Exception {
		Fixtures.assertInvalid(
		    validator.validate(Fixtures.negative("invalid-form-uuid"), JsonSchemaValidator.SchemaType.MODEL_DEFINITION),
		    "invalid form UUID");
	}
	
	@Test
	public void shouldRejectMalformedJson() throws Exception {
		Fixtures.assertInvalid(validator.validate("{not json", JsonSchemaValidator.SchemaType.MODEL_DEFINITION),
		    "malformed JSON");
	}
	
	@Test
	public void shouldRoundTripAStorageEnvelope() throws Exception {
		// an envelope holding the published HIV definition must validate against the envelope schema
		String definition = Fixtures.sample("hiv-model");
		StorageEnvelope envelope = new StorageEnvelope();
		envelope.saveDraft(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(definition), 0);
		envelope.publishDraft("1.0.0", 1, null);
		Fixtures.assertValid(validator.validate(envelope.toJson(new com.fasterxml.jackson.databind.ObjectMapper()),
		    JsonSchemaValidator.SchemaType.STORAGE_ENVELOPE), "published storage envelope");
	}
}
