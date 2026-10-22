package org.openmrs.module.caremodels.resolver;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.Test;
import org.openmrs.module.caremodels.config.*;
import org.springframework.transaction.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class SharedResolverRegistryTest {
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private VersionedDataResolver extension() throws Exception {
		VersionedDataResolver extension = mock(VersionedDataResolver.class);
		when(extension.getId()).thenReturn("test-counter");
		when(extension.getVersion()).thenReturn("2.0.0");
		when(extension.getSourceSchema())
		        .thenReturn(
		            mapper.readTree("{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"type\",\"resolverId\",\"resolverVersion\",\"resultType\"],\"properties\":{\"type\":{\"const\":\"registered\"},\"resolverId\":{\"const\":\"test-counter\"},\"resolverVersion\":{\"const\":\"2.0.0\"},\"resultType\":{\"const\":\"NUMBER\"}}}"));
		when(extension.getResultSchema())
		        .thenReturn(
		            mapper.readTree("{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"type\",\"value\"],\"properties\":{\"type\":{\"const\":\"test-counter\"},\"value\":{\"type\":\"number\"}}}"));
		when(extension.resolve(anyInt(), any(), any())).thenReturn(
		    mapper.readTree("{\"type\":\"test-counter\",\"value\":1}"));
		return extension;
	}
	
	private JsonNode source() throws Exception {
		return mapper
		        .readTree("{\"type\":\"registered\",\"resolverId\":\"test-counter\",\"resolverVersion\":\"2.0.0\",\"resultType\":\"NUMBER\"}");
	}
	
	@Test
	public void validatorAndRuntimeMustUseTheSameExplicitlyRegisteredVersionedCode() throws Exception {
		DataResolverRegistry registry = new DataResolverRegistry();
		registry.setExtensions(Collections.singletonList(extension()));
		assertTrue(SourceContract.validate(source(), registry, "$.source").isValid());
		assertEquals(1, registry.resolve("test-counter", 7, source(), null).path("value").asInt());
		assertEquals(Long.valueOf(1), registry.getMetrics("test-counter").get("calls"));
		assertFalse(SourceContract.validate(source(), new DataResolverRegistry(), "$.source").isValid());
		((com.fasterxml.jackson.databind.node.ObjectNode) source()).put("resolverVersion", "3.0.0");
		JsonNode wrong = mapper
		        .readTree("{\"type\":\"registered\",\"resolverId\":\"test-counter\",\"resolverVersion\":\"3.0.0\",\"resultType\":\"NUMBER\"}");
		assertFalse(SourceContract.validate(wrong, registry, "$.source").isValid());
	}
	
	@Test
	public void malformedResultsMustFailAndBeCountedWithoutCachingPatientData() throws Exception {
		DataResolverRegistry registry = new DataResolverRegistry();
		VersionedDataResolver extension = extension();
		when(extension.resolve(anyInt(), any(), any())).thenReturn(
		    mapper.readTree("{\"type\":\"test-counter\",\"value\":\"not-a-number\"}"));
		registry.setExtensions(Collections.singletonList(extension));
		try {
			registry.resolve("test-counter", 7, source(), null);
			fail();
		}
		catch (IllegalArgumentException expected) {}
		assertEquals(Long.valueOf(1), registry.getMetrics("test-counter").get("failures"));
	}
	
	@Test
	public void productionExecutionMustApplyReadOnlyTenSecondTransactionDeadline() throws Exception {
		DataResolverRegistry registry = new DataResolverRegistry();
		registry.setExtensions(Collections.singletonList(extension()));
		PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
		TransactionStatus status = mock(TransactionStatus.class);
		when(transactions.getTransaction(any())).thenReturn(status);
		registry.setTransactionManager(transactions);
		registry.resolve("test-counter", 7, source(), null);
		org.mockito.ArgumentCaptor<TransactionDefinition> definition = org.mockito.ArgumentCaptor
		        .forClass(TransactionDefinition.class);
		verify(transactions).getTransaction(definition.capture());
		assertEquals(10, definition.getValue().getTimeout());
		assertTrue(definition.getValue().isReadOnly());
		verify(transactions).commit(status);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void extensionMustNeverSilentlyReplaceABuiltinResolver() throws Exception {
		DataResolverRegistry registry = new DataResolverRegistry();
		VersionedDataResolver extension = extension();
		when(extension.getId()).thenReturn("latest-observation");
		registry.setExtensions(Collections.singletonList(extension));
	}
}
