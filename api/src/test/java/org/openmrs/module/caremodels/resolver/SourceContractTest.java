package org.openmrs.module.caremodels.resolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceContractTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private final DataResolverRegistry registry = new DataResolverRegistry();

	private boolean valid(String json) throws Exception {
		return SourceContract.validate(mapper.readTree(json), registry, "$.source").isValid();
	}

	@Test
	public void supportedSourcesShouldRequireExactReferencesAndOptions() throws Exception {
		assertTrue(valid("{\"type\":\"encounter-query\",\"encounterTypeAlias\":\"review\",\"resultType\":\"TABLE\"}"));
		assertTrue(valid("{\"type\":\"program-state\",\"programAlias\":\"hiv\",\"resultType\":\"TABLE\"}"));
		assertTrue(valid("{\"type\":\"registered\",\"resolverId\":\"patient-summary\",\"resolverVersion\":\"1.0.0\",\"resultType\":\"TABLE\",\"fields\":[\"gender\"]}"));
		assertFalse(valid("{\"type\":\"program-state\",\"programAlias\":\"hiv\",\"conceptAlias\":\"wrong\",\"resultType\":\"TABLE\"}"));
		assertFalse(valid("{\"type\":\"latest-observation\",\"conceptAlias\":\"weight\",\"resultType\":\"NUMBER\",\"maxResults\":\"100\"}"));
	}

	@Test
	public void unknownRegisteredMethodsAndVersionsMustStayUnavailable() throws Exception {
		assertFalse(valid("{\"type\":\"registered\",\"resolverId\":\"risk-score\",\"resolverVersion\":\"1.0.0\",\"resultType\":\"NUMBER\"}"));
		assertFalse(valid("{\"type\":\"registered\",\"resolverId\":\"conditions\",\"resolverVersion\":\"2.0.0\",\"resultType\":\"TABLE\"}"));
		assertFalse(valid("{\"type\":\"latest-observation\",\"conceptAlias\":\"weight\",\"resultType\":\"NUMBER\",\"effectiveTime\":\"NOW\"}"));
	}
}
