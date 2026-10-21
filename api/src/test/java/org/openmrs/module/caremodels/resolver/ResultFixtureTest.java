package org.openmrs.module.caremodels.resolver;

import com.fasterxml.jackson.databind.*;
import org.junit.Test;
import org.openmrs.module.caremodels.config.*;
import static org.junit.Assert.*;

public class ResultFixtureTest {

	@Test
	public void handoffFixturesMustValidateAgainstVersionedResultSchemas() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		JsonSchemaValidator validator = new JsonSchemaValidator();
		for (String name : new String[] { "scalar", "status", "time-series", "observation-table", "encounters",
		        "conditions", "diagnoses", "medications", "programs", "patient" }) {
			JsonNode result = mapper.readTree(getClass().getResourceAsStream("/results/" + name + ".json"));
			ValidationResult validation = validator.validate(result, JsonSchemaValidator.SchemaType.CLINICAL_RESULT);
			assertTrue(name + validation.toString(), validation.isValid());
			for (JsonNode value : result.path("values")) {
				assertFalse(value.has("comparator"));
				assertFalse(value.has("qualifier"));
				if ("NUMBER".equals(value.path("valueType").asText()))
					assertTrue(value.path("value").isNumber());
			}
		}
	}

	@Test
	public void qualifiedNumericStringMustNeverValidateAsAnExactNumber() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode result = mapper.readTree(getClass().getResourceAsStream("/results/observation-table.json"));
		((com.fasterxml.jackson.databind.node.ObjectNode) result.path("values").get(0)).put("valueType", "NUMBER");
		assertFalse(new JsonSchemaValidator().validate(result, JsonSchemaValidator.SchemaType.CLINICAL_RESULT).isValid());
	}
}
