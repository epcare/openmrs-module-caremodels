package org.openmrs.module.caremodels.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** The incoming rule checks and widget branch contracts must hold simultaneously. */
public class MergedSemanticContractTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private SemanticValidator semantic;

	@Before
	public void setup() {
		OpenmrsReferenceChecker checker = mock(OpenmrsReferenceChecker.class);
		when(checker.conceptExists(anyString())).thenReturn(true);
		when(checker.programExists(anyString())).thenReturn(true);
		when(checker.encounterTypeExists(anyString())).thenReturn(true);
		when(checker.formExists(anyString())).thenReturn(true);
		semantic = new SemanticValidator(checker);
	}

	private ObjectNode model() throws Exception {
		return (ObjectNode) mapper.readTree(Fixtures.sample("hiv-model"));
	}

	@Test
	public void numericObservationCollectionsMustRemainValidForExistingChartsAndTables() throws Exception {
		ObjectNode definition = model();
		((ObjectNode) definition.path("dataSources").path("viral_load_history")).put("resultType", "NUMBER");
		assertTrue(semantic.validateDefinition(definition).toString(), semantic.validateDefinition(definition).isValid());
	}

	@Test
	public void metricMustRejectCollectionResultTypes() throws Exception {
		ObjectNode definition = model();
		((ObjectNode) definition.path("dataSources").path("cd4_latest")).put("resultType", "TABLE");
		assertFalse(semantic.validateDefinition(definition).isValid());
	}

	@Test
	public void staticWidgetsAndMultiBindingsMustCoexistWithNestedEnrollmentValidation() throws Exception {
		ObjectNode definition = model();
		ArrayNode widgets = (ArrayNode) definition.path("workspace").path("tabs").get(0).path("widgets");
		widgets.addObject().put("id", "note").put("type", "text-note").putObject("properties").put("text", "Plain guidance");
		ObjectNode chart = widgets.addObject().put("id", "series").put("type", "trend-chart");
		chart.putArray("dataSources").add("viral_load_history").add("cd4_latest");
		ValidationResult valid = semantic.validateDefinition(definition);
		assertTrue(valid.toString(), valid.isValid());
		ArrayNode rules = (ArrayNode) definition.path("enrollment").path("rules");
		rules.addObject()
		        .put("id", "missing-rule")
		        .put("action", "AUTO_ENROLL")
		        .set(
		            "expression",
		            mapper.readTree("{\"all\":[{\"type\":\"observation\",\"operator\":\"EXISTS\",\"alias\":\"undeclared\"}]}"));
		assertFalse(semantic.validateDefinition(definition).isValid());
	}

	@Test
	public void registeredSourcesMustStillRequireExecutableApprovedVersions() throws Exception {
		ObjectNode definition = model();
		((ObjectNode) definition.path("dataSources")).putObject("unsupported").put("type", "registered")
		        .put("resolverId", "arbitrary-class").put("resolverVersion", "1.0.0").put("resultType", "TABLE");
		assertFalse(semantic.validateDefinition(definition).isValid());
	}
}
