package org.openmrs.module.caremodels.web.resource;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.*;
import org.mockito.MockedStatic;
import org.openmrs.*;
import org.openmrs.api.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.CareModelAccessService;
import org.openmrs.module.caremodels.api.CareModelAccessService.AuthorizedAccess;
import org.openmrs.module.caremodels.resolver.*;
import org.openmrs.module.caremodels.workspace.WorkspaceDescriptors;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class PatientResourceContractTest {
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private MockedStatic<Context> context;
	
	private CareModelAccessService accessService;
	
	private ObjectNode definition;
	
	private DataResolver resolver;
	
	private DataRequestResource resource;
	
	private Patient patient;
	
	@Before public void setup() throws Exception {
        context = mockStatic(Context.class);
        accessService = mock(CareModelAccessService.class);
        context.when(() -> Context.getService(CareModelAccessService.class)).thenReturn(accessService);
        PatientService patients = mock(PatientService.class); patient = new Patient(7); patient.setUuid("patient");
        when(patients.getPatientByUuid("patient")).thenReturn(patient);
        context.when(Context::getPatientService).thenReturn(patients);
        definition = (ObjectNode) mapper.readTree("{\"terminology\":{\"concepts\":{\"weight\":\"concept\"}},\"dataSources\":{\"first\":{\"type\":\"latest-observation\",\"conceptAlias\":\"weight\",\"resultType\":\"NUMBER\"},\"otherTab\":{\"type\":\"latest-observation\",\"conceptAlias\":\"weight\",\"resultType\":\"NUMBER\"}},\"workspace\":{\"tabs\":[{\"id\":\"overview\",\"label\":\"Overview\",\"widgets\":[{\"id\":\"metric\",\"type\":\"metric\",\"dataSource\":\"first\"}]}]},\"forms\":[],\"actions\":[]}");
        when(accessService.authorizeWorkspace("patient", "model", "overview")).thenAnswer(call -> authorized());
        resolver = mock(DataResolver.class); when(resolver.getId()).thenReturn("latest-observation");
        when(resolver.getReferenceKind()).thenReturn("concepts"); when(resolver.getReferenceField()).thenReturn("conceptAlias");
        when(resolver.resolve(eq(7), any(), eq("concept"))).thenReturn(mapper.readTree("{\"type\":\"latest-observation\",\"concept\":\"concept\",\"values\":[],\"resolverVersion\":\"1.0.0\",\"assessedAt\":\"2026-10-22T00:00:00Z\",\"provenance\":\"OpenMRS service API\",\"status\":\"no-records\"}"));
        DataResolverRegistry registry = new DataResolverRegistry(); registry.register(resolver);
        resource = new DataRequestResource(registry);
    }
	
	@After
	public void cleanup() {
		context.close();
	}
	
	private AuthorizedAccess authorized() {
		JsonNode tab = definition.path("workspace").path("tabs").get(0);
		return new AuthorizedAccess("patient", "model", "model-uuid", "member", definition, tab, mapper.createArrayNode());
	}
	
	private DataRequestResult request(String key) {
		DataRequestResult request = new DataRequestResult();
		request.setPatient("patient");
		request.setModel("model");
		request.setTab("overview");
		request.setDataSource(key);
		return request;
	}
	
	@Test
	public void dataShouldReauthorizeAndSerializeResultAsJsonString() throws Exception {
		DataRequestResult response = resource.save(request("first"));
		assertEquals("latest-observation", mapper.readTree(response.getResultJson()).path("type").asText());
		assertEquals("member", response.getMemberUuid());
		resource.save(request("first"));
		verify(accessService, times(2)).authorizeWorkspace("patient", "model", "overview");
	}
	
	@Test
	public void unselectedTabOrUnboundSourceShouldMakeZeroClinicalCalls() {
		try {
			resource.save(request("otherTab"));
			fail();
		}
		catch (APIException expected) {}
		try {
			resource.save(request("undeclared"));
			fail();
		}
		catch (APIException expected) {}
		verify(resolver, never()).resolve(any(), any(), any());
	}
	
	@Test
	public void deniedPatientModelOrMembershipShouldMakeZeroClinicalCalls() {
		when(accessService.authorizeWorkspace(anyString(), anyString(), anyString())).thenThrow(new APIException("denied"));
		try {
			resource.save(request("first"));
			fail();
		}
		catch (APIException expected) {}
		verify(resolver, never()).resolve(any(), any(), any());
	}
	
	@Test
	public void everyMultiSourceBindingMustBeDeclaredOnSelectedTab() {
		ObjectNode widget = (ObjectNode) definition.path("workspace").path("tabs").get(0).path("widgets").get(0);
		widget.remove("dataSource");
		widget.put("type", "trend-chart");
		widget.putArray("dataSources").add("first").add("otherTab");
		resource.save(request("first"));
		resource.save(request("otherTab"));
		verify(resolver, times(2)).resolve(eq(7), any(), eq("concept"));
		try {
			resource.save(request("foreign-model-source"));
			fail();
		}
		catch (APIException expected) {}
	}
	
	@Test
	public void missingPatientMustNeverResolveAnUnscopedClinicalQuery() {
		patient.setVoided(true);
		try {
			resource.save(request("first"));
			fail();
		}
		catch (APIException expected) {}
		verify(resolver, never()).resolve(any(), any(), any());
	}
	
	@Test
	public void workspaceDiscoveryShouldNotExecuteResolversOrExposeOtherTabs() {
		JsonNode descriptors = new WorkspaceDescriptors().resolve(authorized());
		assertEquals(0, descriptors.path("forms").size());
		assertEquals(0, descriptors.path("actions").size());
		verify(resolver, never()).resolve(any(), any(), any());
		assertTrue(new WorkspaceResource().getRepresentationDescription(new DefaultRepresentation()).getProperties()
		        .containsKey("descriptors"));
	}
	
	@Test public void formDescriptorsShouldResolveMultipleSelectedReferencesAndKeepUnsupportedLaunchersDisabled() throws Exception {
        ((ObjectNode)definition.path("terminology")).putObject("forms").put("one", "form-1").put("two", "form-2").put("unselected", "form-3");
        ((ArrayNode)definition.path("forms")).addObject().put("alias", "one").put("formUuid", "form-1");
        ((ArrayNode)definition.path("forms")).addObject().put("alias", "two").put("formUuid", "form-2");
        ((ObjectNode)definition.path("workspace").path("tabs").get(0).path("widgets").get(0)).putArray("formAliases").add("one").add("two");
        context.when(() -> Context.hasPrivilege(anyString())).thenReturn(true);
        FormService forms = mock(FormService.class); context.when(Context::getFormService).thenReturn(forms);
        Form first = new Form(); first.setUuid("form-1"); first.setName("First"); first.setPublished(false);
        Form second = new Form(); second.setUuid("form-2"); second.setName("Second"); second.setRetired(true);
        when(forms.getFormByUuid("form-1")).thenReturn(first); when(forms.getFormByUuid("form-2")).thenReturn(second);
        JsonNode descriptors = new WorkspaceDescriptors().resolve(authorized());
        assertEquals(2, descriptors.path("forms").size());
        assertEquals("form-unpublished", descriptors.path("forms").get(0).path("reason").asText());
        assertEquals("form-retired", descriptors.path("forms").get(1).path("reason").asText());
        verify(forms, never()).getFormByUuid("form-3"); verify(resolver, never()).resolve(any(), any(), any());
    }
	
	@Test
	public void arbitraryActionTargetsMustNotBecomeExecutableDescriptors() {
		((ArrayNode) definition.path("actions")).addObject().put("id", "unsafe").put("type", "navigate-internal")
		        .put("label", "List").put("target", "arbitrary/path");
		((ObjectNode) definition.path("workspace").path("tabs").get(0).path("widgets").get(0)).putArray("actionIds").add(
		    "unsafe");
		JsonNode action = new WorkspaceDescriptors().resolve(authorized()).path("actions").get(0);
		assertFalse(action.path("available").asBoolean());
		assertFalse(action.has("target"));
		assertEquals("registered-authorized-destination-required", action.path("reason").asText());
	}
	
	@Test public void missingAndPublishedFormsMustNotBecomeLaunchableFromCatalogMetadataAlone() throws Exception {
        ((ObjectNode)definition.path("terminology")).putObject("forms").put("one", "form-1");
        ((ArrayNode)definition.path("forms")).addObject().put("alias", "one").put("formUuid", "form-1");
        ((ObjectNode)definition.path("workspace").path("tabs").get(0).path("widgets").get(0)).put("formAlias", "one");
        FormService forms = mock(FormService.class); context.when(Context::getFormService).thenReturn(forms);
        assertEquals("form-read-not-authorized", new WorkspaceDescriptors().resolve(authorized()).path("forms").get(0).path("reason").asText());
        verifyNoInteractions(forms);
        context.when(() -> Context.hasPrivilege(anyString())).thenReturn(true);
        assertEquals("form-not-found", new WorkspaceDescriptors().resolve(authorized()).path("forms").get(0).path("reason").asText());
        Form form = new Form(); form.setUuid("form-1"); form.setName("Form"); form.setPublished(true);
        EncounterType encounterType = new EncounterType(); encounterType.setUuid("type"); encounterType.setName("Review"); form.setEncounterType(encounterType);
        when(forms.getFormByUuid("form-1")).thenReturn(form);
        when(forms.getFormResourcesForForm(form)).thenReturn(Collections.emptyList());
        assertEquals("form-engine-resource-unavailable", new WorkspaceDescriptors().resolve(authorized()).path("forms").get(0).path("reason").asText());
        FormResource metadata = new FormResource(); metadata.setName("JSON schema");
        when(forms.getFormResourcesForForm(form)).thenReturn(Collections.singletonList(metadata));
        JsonNode resolved = new WorkspaceDescriptors().resolve(authorized()).path("forms").get(0);
        assertTrue(resolved.path("available").asBoolean());
        assertEquals("", resolved.path("reason").asText());
        assertTrue(resolved.path("resourceNames").size() > 0);
        ObjectNode launch = ((ArrayNode)definition.path("actions")).addObject().put("id", "launch")
            .put("type", "launch-form").put("formAlias", "one");
        ((ObjectNode)definition.path("workspace").path("tabs").get(0).path("widgets").get(0)).putArray("actionIds").add("launch");
        assertTrue(new WorkspaceDescriptors().resolve(authorized()).path("actions").get(0).path("available").asBoolean());
        launch.putObject("availabilityRule").put("literal", "TRUE");
        assertFalse(new WorkspaceDescriptors().resolve(authorized()).path("actions").get(0).path("available").asBoolean());

    }
}
