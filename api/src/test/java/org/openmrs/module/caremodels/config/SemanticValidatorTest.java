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

import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Semantic validation with a stubbed reference checker (all UUIDs exist). Structural validity is a
 * precondition; these tests exercise the OpenMRS-aware cross-reference pass.
 */
public class SemanticValidatorTest {
	
	private static final class AllReferencesExist implements OpenmrsReferenceChecker {
		
		@Override
		public boolean conceptExists(String uuid) {
			return true;
		}
		
		@Override
		public boolean conceptIsSet(String uuid) {
			return true;
		}
		
		@Override
		public boolean programExists(String uuid) {
			return true;
		}
		
		@Override
		public boolean encounterTypeExists(String uuid) {
			return true;
		}
		
		@Override
		public boolean formExists(String uuid) {
			return true;
		}
		
		@Override
		public boolean privilegeExists(String name) {
			return true;
		}
	}
	
	private ObjectMapper mapper;
	
	private SemanticValidator validator;
	
	@Before
	public void setup() {
		mapper = new ObjectMapper();
		validator = new SemanticValidator(new AllReferencesExist());
	}
	
	private JsonNode parse(String name) throws Exception {
		return mapper.readTree(Fixtures.sample(name));
	}
	
	@Test
	public void shouldAcceptValidHivModel() throws Exception {
		Fixtures.assertValid(validator.validateDefinition(parse("hiv-model")), "HIV model");
	}
	
	@Test
	public void shouldAcceptValidAncAndNcdModels() throws Exception {
		Fixtures.assertValid(validator.validateDefinition(parse("anc-model")), "ANC model");
		Fixtures.assertValid(validator.validateDefinition(parse("ncd-model")), "NCD model");
	}
	
	@Test
	public void shouldRejectDataSourceConceptAliasNotDeclaredInTerminology() throws Exception {
		JsonNode definition = parse("hiv-model");
		// remove the viral_load concept alias; the viral_load_history data source still references it
		((com.fasterxml.jackson.databind.node.ObjectNode) definition.path("terminology").path("concepts"))
		        .remove("viral_load");
		Fixtures.assertInvalid(validator.validateDefinition(definition), "undeclared concept alias");
	}
	
	@Test
	public void shouldRejectWidgetReferencingUndeclaredDataSource() throws Exception {
		JsonNode definition = parse("hiv-model");
		// remove the cd4_latest data source; the cd4-metric widget still references it
		((com.fasterxml.jackson.databind.node.ObjectNode) definition.path("dataSources")).remove("cd4_latest");
		ValidationResult result = validator.validateDefinition(definition);
		Fixtures.assertInvalid(result, "undeclared data source");
		assertTrue("error should name the data source key, got: " + result, result.toString().contains("cd4_latest"));
	}
	
	@Test
	public void shouldRejectWidgetReferencingUndeclaredAction() throws Exception {
		JsonNode definition = parse("hiv-model");
		// remove the action the action-panel widget references
		((com.fasterxml.jackson.databind.node.ArrayNode) definition.path("actions")).remove(0);
		Fixtures.assertInvalid(validator.validateDefinition(definition), "undeclared action");
	}
	
	@Test
	public void shouldRejectActionLaunchingUndeclaredForm() throws Exception {
		JsonNode definition = parse("hiv-model");
		// remove the form declaration; the launch-form action still references the alias
		((com.fasterxml.jackson.databind.node.ArrayNode) definition.path("forms")).remove(0);
		Fixtures.assertInvalid(validator.validateDefinition(definition), "launch-form with undeclared form");
	}
	
	@Test
	public void shouldRejectDefaultTabThatDoesNotExist() throws Exception {
		JsonNode definition = parse("hiv-model");
		((com.fasterxml.jackson.databind.node.ObjectNode) definition.path("workspace")).put("defaultTab", "nope");
		Fixtures.assertInvalid(validator.validateDefinition(definition), "bogus defaultTab");
	}
	
	@Test
	public void shouldRejectBannedPrivilegeKeyEvenWhenSchemaIsBypassed() throws Exception {
		// construct in-memory: bypass the schema to prove the semantic pass is an independent guard
		JsonNode definition = mapper.readTree(Fixtures.sample("minimal-model"));
		((com.fasterxml.jackson.databind.node.ObjectNode) definition.path("metadata").path("display")).put("privilege",
		    "View Care Models");
		ValidationResult result = validator.validateDefinition(definition);
		Fixtures.assertInvalid(result, "banned privilege key");
	}
	
	@Test
	public void shouldRejectArbitraryUrlInWidgetProperties() throws Exception {
		Fixtures.assertInvalid(validator.validateDefinition(mapper.readTree(Fixtures.negative("arbitrary-url"))),
		    "arbitrary URL in widget properties");
	}
	
	@Test
	public void shouldRejectUnregisteredWidgetTypeDirectly() throws Exception {
		Fixtures.assertInvalid(validator.validateDefinition(mapper.readTree(Fixtures.negative("unregistered-widget"))),
		    "unregistered widget type");
	}
	
	@Test
	public void shouldRejectFacilityOverlayEvenWhenSchemaIsBypassed() throws Exception {
		JsonNode definition = mapper.readTree(Fixtures.sample("minimal-model"));
		((com.fasterxml.jackson.databind.node.ObjectNode) definition).set("facilities",
		    mapper.readTree("[{\"uuid\": \"x\"}]"));
		Fixtures.assertInvalid(validator.validateDefinition(definition), "facilities key");
	}
	
	@Test
	public void shouldValidateAliasesInsideNestedRules() throws Exception {
		JsonNode definition = parse("minimal-model");
		((com.fasterxml.jackson.databind.node.ObjectNode) definition.path("workspace").path("tabs").get(0)).set(
		    "visibilityRule",
		    mapper.readTree("{\"all\":[{\"type\":\"observation\",\"operator\":\"EXISTS\",\"alias\":\"missing\"}]}"));
		Fixtures.assertInvalid(validator.validateDefinition(definition), "nested undeclared rule alias");
	}
	
	@Test
	public void shouldRejectLayoutReferencesAndGridOverflow() throws Exception {
		JsonNode definition = parse("minimal-model");
		((com.fasterxml.jackson.databind.node.ObjectNode) definition.path("workspace").path("tabs").get(0)).set("layouts",
		    mapper.readTree("{\"wide\":[{\"widgetId\":\"missing\",\"row\":0,\"col\":12,\"w\":2,\"h\":1}]}"));
		ValidationResult result = validator.validateDefinition(definition);
		assertFalse(result.isValid());
		assertTrue(result.toString(), result.toString().contains("undeclared widget"));
		assertTrue(result.toString(), result.toString().contains("12-column"));
	}
	
	@Test
	public void shouldRejectIncompatibleWidgetResultType() throws Exception {
		JsonNode definition = parse("hiv-model");
		((com.fasterxml.jackson.databind.node.ObjectNode) definition.path("dataSources").path("viral_load_history")).put(
		    "resultType", "STRING");
		Fixtures.assertInvalid(validator.validateDefinition(definition), "trend chart result mismatch");
	}
	
	@Test
	public void shouldRejectTriggerWithUndeclaredAlias() throws Exception {
		JsonNode definition = parse("minimal-model");
		((com.fasterxml.jackson.databind.node.ObjectNode) definition.path("enrollment")).set("triggers",
		    mapper.readTree("[{\"type\":\"OBSERVATION_SAVED\",\"conceptAlias\":\"missing\"}]"));
		Fixtures.assertInvalid(validator.validateDefinition(definition), "trigger alias");
	}
	
	@Test
	public void shouldRejectTransitionWithUndeclaredStage() throws Exception {
		JsonNode definition = parse("minimal-model");
		com.fasterxml.jackson.databind.node.ObjectNode pathways = (com.fasterxml.jackson.databind.node.ObjectNode) definition
		        .path("pathways");
		pathways.set("stages", mapper.readTree("[{\"id\":\"start\",\"label\":\"Start\"}]"));
		pathways.set("transitions", mapper.readTree("[{\"from\":\"start\",\"to\":\"missing\"}]"));
		Fixtures.assertInvalid(validator.validateDefinition(definition), "pathway stage reference");
	}
}
