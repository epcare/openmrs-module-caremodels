package org.openmrs.module.caremodels.api;

import java.util.*;
import org.junit.*;
import org.mockito.MockedStatic;
import org.openmrs.*;
import org.openmrs.api.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.api.impl.CareModelAccessServiceImpl;
import org.openmrs.module.caremodels.model.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class AccessBoundaryTest {
    private MockedStatic<Context> context;
    private CareModelAccessServiceImpl access;
    private CareModelMemberService members;
    private CareModelDAO dao;
    private Patient patient;
    private CareModel model;
    private CareModelMember member;
    private final Set<String> privileges = new HashSet<>();

    @Before public void setup() {
        context = mockStatic(Context.class);
        privileges.addAll(Arrays.asList("View Care Models", "View Patients", "View Example"));
        context.when(() -> Context.hasPrivilege(anyString())).thenAnswer(call -> privileges.contains(call.getArgument(0)));
        patient = new Patient(7); patient.setUuid("patient");
        PatientService patients = mock(PatientService.class); when(patients.getPatientByUuid("patient")).thenReturn(patient);
        context.when(Context::getPatientService).thenReturn(patients);
        members = mock(CareModelMemberService.class); dao = mock(CareModelDAO.class);
        access = new CareModelAccessServiceImpl(); access.setMemberService(members); access.setCareModelDAO(dao);
        model = new CareModel(); model.setId(3); model.setUuid("model-uuid"); model.setCode("model"); model.setPrivilege("View Example"); model.setStatus(CareModel.CareModelStatus.PUBLISHED);
        model.setConfiguration("{\"published\":{\"definition\":{\"workspace\":{\"tabs\":[{\"id\":\"overview\",\"label\":\"Overview\",\"widgets\":[]}]}}}}");
        when(dao.getCareModelByCode("model")).thenReturn(model);
        member = new CareModelMember(); member.setUuid("member"); member.setStatus(CareModelMember.MemberStatus.ACTIVE); member.setPatientId(7); member.setCareModelId(3);
        when(members.getCurrentMember(7, 3)).thenReturn(member);
    }
    @After public void cleanup() { if (context != null) context.close(); }
    private void denied() { try { access.authorizeWorkspace("patient", "model", "overview"); fail(); } catch (APIException expected) { } }
    @Test public void shouldAuthorizeClinicalViewerWithoutAdminEnvelopeRead() {
        assertEquals("overview", access.authorizeWorkspace("patient", "model", null).getTabDefinition().path("id").asText());
        context.verify(() -> Context.getService(CareModelService.class), never());
        context.verify(Context::getObsService, never());
    }
    @Test public void shouldDenyMissingMasterModelAndPatientPrivileges() {
        for (String privilege : Arrays.asList("View Care Models", "View Example", "View Patients")) {
            privileges.remove(privilege); denied(); privileges.add(privilege);
        }
        context.verify(Context::getObsService, never());
    }
    @Test public void shouldDenySuspendedCompletedVoidedAndMissingMembership() {
        for (CareModelMember.MemberStatus status : Arrays.asList(CareModelMember.MemberStatus.SUSPENDED, CareModelMember.MemberStatus.COMPLETED)) {
            member.setStatus(status); denied();
        }
        member.setStatus(CareModelMember.MemberStatus.ACTIVE); member.setVoided(true); denied();
        when(members.getCurrentMember(7, 3)).thenReturn(null); denied();
    }
    @Test public void shouldDenyForeignPatientAndUnknownTabWithoutClinicalCalls() {
        try { access.authorizeWorkspace("foreign", "model", "overview"); fail(); } catch (APIException expected) { }
        try { access.authorizeWorkspace("patient", "model", "unknown"); fail(); } catch (APIException expected) { }
        context.verify(Context::getObsService, never());
    }
    @Test public void membershipLookupMustNeverReadDefinitionsOrResolveClinicalData() {
        when(members.getActiveMembersByPatient(7)).thenReturn(Collections.emptyList());
        assertEquals(0, access.getAuthorizedMemberships("patient").size());
        verify(dao, never()).getCareModelByCode(anyString());
        context.verify(Context::getObsService, never()); context.verify(Context::getEncounterService, never());
    }

    @Test public void pathwayShouldExposeOnlyRecordedSelectedStageWithoutInferredCompletion() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode envelope = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(model.getConfiguration());
        com.fasterxml.jackson.databind.node.ObjectNode definition = (com.fasterxml.jackson.databind.node.ObjectNode)envelope.path("published").path("definition");
        definition.putObject("pathways").put("enabled", true).putArray("stages").addObject().put("id", "review").put("label", "Review");
        ((com.fasterxml.jackson.databind.node.ArrayNode)definition.path("workspace").path("tabs").get(0).path("widgets")).addObject().put("id", "plan").put("type", "care-plan");
        model.setConfiguration(envelope.toString());
        member.setMembershipData("{\"schemaVersion\":\"1.0.0\",\"enrollment\":{\"definitionVersion\":\"1.0.0\"},\"pathway\":{\"stage\":\"review\",\"enteredAt\":\"2026-01-01T00:00:00Z\"}}");
        com.fasterxml.jackson.databind.JsonNode result = access.authorizeWorkspace("patient", "model", "overview").getMemberPathway();
        assertEquals("recorded", result.path("status").asText()); assertEquals("review", result.path("stage").asText()); assertFalse(result.has("completed"));
        member.setMembershipData(null);
        assertEquals("insufficient-data", access.authorizeWorkspace("patient", "model", "overview").getMemberPathway().path("status").asText());
        ((com.fasterxml.jackson.databind.node.ArrayNode)definition.path("workspace").path("tabs").get(0).path("widgets")).removeAll();
        model.setConfiguration(envelope.toString());
        assertNull(access.authorizeWorkspace("patient", "model", "overview").getMemberPathway());
    }
}
