/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.api;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Privilege;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.dto.CareModelMembershipDTO;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.model.CareModelMember.MemberStatus;
import org.openmrs.module.caremodels.security.CareModelsPrivileges;
import org.openmrs.test.BaseModuleContextSensitiveTest;

/**
 * Authorization chain tests for {@link CareModelAccessService}: acceptance criteria 1-5 at the
 * service layer. Every failure mode must fail closed and must not disclose restricted model names.
 */
public class CareModelAccessServiceTest extends BaseModuleContextSensitiveTest {
	
	private CareModelService modelService;
	
	private CareModelMemberService memberService;
	
	private CareModelAccessService accessService;
	
	private ObjectMapper mapper;
	
	private User originalUser;
	
	@Before
	public void setup() throws Exception {
		modelService = Context.getService(CareModelService.class);
		memberService = Context.getService(CareModelMemberService.class);
		accessService = Context.getService(CareModelAccessService.class);
		mapper = new ObjectMapper();
		originalUser = Context.getAuthenticatedUser();
	}
	
	@After
	public void restoreUser() throws Exception {
		// the base test harness authenticates as admin with junit.password (default "test")
		if (!"admin".equals(Context.getAuthenticatedUser().getUsername())) {
			Context.authenticate("admin", System.getProperty("junit.password", "test"));
		}
	}
	
	// ========================
	// helpers
	// ========================
	
	private String minimalDefinition(String label) {
		ObjectNode definition = mapper.createObjectNode();
		definition.put("schemaVersion", "1.0.0");
		ObjectNode metadata = definition.putObject("metadata");
		metadata.put("category", "SUPPORT");
		metadata.putObject("display").put("label", label).put("order", 10);
		definition.putObject("terminology");
		ObjectNode enrollment = definition.putObject("enrollment");
		enrollment.put("enabled", false);
		enrollment.putObject("manualEnrollment").put("enabled", false).put("requiresReason", true);
		definition.putObject("dataSources");
		ObjectNode workspace = definition.putObject("workspace");
		workspace.put("defaultTab", "overview");
		ObjectNode tab = workspace.putArray("tabs").addObject();
		tab.put("id", "overview");
		tab.put("label", "Overview");
		tab.putArray("widgets");
		definition.putArray("forms");
		definition.putArray("actions");
		ObjectNode pathways = definition.putObject("pathways");
		pathways.put("enabled", false);
		pathways.putArray("stages");
		pathways.putArray("transitions");
		definition.putObject("automation").putArray("rules");
		ObjectNode lifecycle = definition.putObject("lifecycle");
		lifecycle.putObject("completion").put("mode", "MANUAL");
		lifecycle.put("onSourceCorrection", "FLAG_FOR_REVIEW");
		return definition.toString();
	}
	
	private Privilege privilege(String name) {
		Privilege privilege = Context.getUserService().getPrivilege(name);
		if (privilege == null) {
			privilege = Context.getUserService().savePrivilege(new Privilege(name, "test"));
		}
		return privilege;
	}
	
	private String modelPrivilege() {
		return "Task: caremodels.access.test";
	}
	
	private CareModel enrolledPublishedModel(String code, org.openmrs.Patient patient) throws Exception {
		// ensure the model-view privilege exists before the model is created
		privilege(modelPrivilege());
		
		CareModel model = modelService.createCareModel(code, "Access Test " + code, null, modelPrivilege(),
		    minimalDefinition(code));
		modelService.publishCareModel(code, "1.0.0", 1);
		enroll(patient, model);
		return model;
	}
	
	private void enroll(org.openmrs.Patient patient, CareModel model) {
		CareModelMember member = new CareModelMember();
		member.setCareModelId(model.getId());
		member.setPatientId(patient.getPatientId());
		member.setStatus(MemberStatus.ACTIVE);
		member.setDateEnrolled(new Date());
		memberService.saveMember(member);
	}
	
	// ========================
	// tests
	// ========================
	
	@Test
	public void shouldReturnAuthorizedMembershipForPublishedModel() throws Exception {
		org.openmrs.Patient patient = createTestPatient();
		enrolledPublishedModel("ACCESS_T1", patient);
		
		List<CareModelMembershipDTO> memberships = accessService.getAuthorizedMemberships(patient.getUuid());
		
		Assert.assertEquals(1, memberships.size());
		CareModelMembershipDTO dto = memberships.get(0);
		Assert.assertEquals("ACCESS_T1", dto.getModelCode());
		Assert.assertNotNull(dto.getMemberUuid());
		Assert.assertNotNull(dto.getModelUuid());
		Assert.assertNotNull(dto.getDateEnrolled());
	}
	
	@Test
	public void shouldReturnEmptyBoardForPatientWithoutMemberships() throws Exception {
		org.openmrs.Patient patient = createTestPatient();
		Assert.assertTrue(accessService.getAuthorizedMemberships(patient.getUuid()).isEmpty());
	}
	
	@Test
	public void shouldFilterDraftModelFromMembership() throws Exception {
		org.openmrs.Patient patient = createTestPatient();
		privilege(modelPrivilege());
		// enrolled but never published
		CareModel draft = modelService.createCareModel("ACCESS_D1", "Draft model", null, modelPrivilege(),
		    minimalDefinition("D1"));
		enroll(patient, draft);
		
		Assert.assertTrue("draft models must not appear as tabs", accessService.getAuthorizedMemberships(patient.getUuid())
		        .isEmpty());
	}
	
	@Test
	public void shouldFilterModelWithoutPrivilegeForRestrictedUser() throws Exception {
		org.openmrs.Patient patient = createTestPatient();
		CareModel model = enrolledPublishedModel("ACCESS_P1", patient);
		
		// a user with the master privilege but NOT the model's required privilege
		User restricted = createUser("accessrestricted", CareModelsPrivileges.VIEW_CARE_MODELS, "View Patients",
		    "Get Patients");
		Context.authenticate(restricted.getUsername(), "Test1234!");
		try {
			Assert.assertTrue("model privilege missing must hide the model",
			    accessService.getAuthorizedMemberships(patient.getUuid()).isEmpty());
			try {
				accessService.authorizeWorkspace(patient.getUuid(), model.getCode(), "overview");
				Assert.fail("workspace access must fail closed without the model privilege (AC2)");
			}
			catch (APIException e) {
				// must not disclose the restricted model name
				Assert.assertFalse(e.getMessage().contains("ACCESS_P1"));
			}
		}
		finally {
			Context.authenticate("admin", System.getProperty("junit.password", "test"));
		}
	}
	
	@Test
	public void shouldDenyUserWithoutMasterPrivilege() throws Exception {
		org.openmrs.Patient patient = createTestPatient();
		enrolledPublishedModel("ACCESS_M1", patient);
		
		User denied = createUser("accessdenied", "View Patients", "Get Patients");
		Context.authenticate(denied.getUsername(), "Test1234!");
		try {
			try {
				accessService.getAuthorizedMemberships(patient.getUuid());
				Assert.fail("membership listing must require the master privilege (AC1)");
			}
			catch (APIException e) {
				// expected: privilege interceptor or explicit check
			}
			try {
				accessService.requireMasterPrivilege();
				Assert.fail("requireMasterPrivilege must throw without the privilege");
			}
			catch (APIException e) {
				// expected
			}
		}
		finally {
			Context.authenticate("admin", System.getProperty("junit.password", "test"));
		}
	}
	
	@Test
	public void shouldDenyWorkspaceWithoutActiveMembership() throws Exception {
		org.openmrs.Patient enrolledPatient = createTestPatient();
		org.openmrs.Patient otherPatient = createTestPatient();
		enrolledPublishedModel("ACCESS_W1", enrolledPatient);
		
		try {
			accessService.authorizeWorkspace(otherPatient.getUuid(), "ACCESS_W1", "overview");
			Assert.fail("workspace access without membership must fail closed (AC3)");
		}
		catch (APIException e) {
			// expected
		}
	}
	
	@Test
	public void shouldAuthorizeWorkspaceForRequestedTabOnly() throws Exception {
		org.openmrs.Patient patient = createTestPatient();
		enrolledPublishedModel("ACCESS_A1", patient);
		
		CareModelAccessService.AuthorizedAccess access = accessService.authorizeWorkspace(patient.getUuid(), "ACCESS_A1",
		    "overview");
		Assert.assertNotNull(access);
		Assert.assertEquals("ACCESS_A1", access.getModelCode());
		Assert.assertEquals("overview", access.getTabDefinition().path("id").asText());
		Assert.assertNotNull(access.getPublishedDefinition());
		
		try {
			accessService.authorizeWorkspace(patient.getUuid(), "ACCESS_A1", "bogus-tab");
			Assert.fail("a tab outside the definition must not resolve");
		}
		catch (APIException e) {
			// expected
		}
	}
	
	@Test
	public void shouldDenyWorkspaceForSuspendedMembership() throws Exception {
		org.openmrs.Patient patient = createTestPatient();
		CareModel model = enrolledPublishedModel("ACCESS_SUSPENDED", patient);
		CareModelMember member = memberService.getCurrentMember(patient.getPatientId(), model.getId());
		member.setStatus(MemberStatus.SUSPENDED);
		memberService.saveMember(member);
		
		try {
			accessService.authorizeWorkspace(patient.getUuid(), model.getCode(), "overview");
			Assert.fail("suspended memberships must not authorize a workspace");
		}
		catch (APIException e) {
			Assert.assertTrue(e.getMessage().contains("No active membership"));
		}
	}
	
	@Test
	public void shouldAuthorizeClinicianWithoutModelManagementPrivilege() throws Exception {
		org.openmrs.Patient patient = createTestPatient();
		CareModel model = enrolledPublishedModel("ACCESS_CLINICIAN", patient);
		
		User clinician = createUser("accessclinician", CareModelsPrivileges.VIEW_CARE_MODELS, modelPrivilege(),
		    "View Patients", "Get Patients");
		Context.authenticate(clinician.getUsername(), "Test1234!");
		try {
			Assert.assertFalse(Context.hasPrivilege(CareModelsPrivileges.MANAGE_CARE_MODELS));
			CareModelAccessService.AuthorizedAccess access = accessService.authorizeWorkspace(patient.getUuid(),
			    model.getCode(), "overview");
			Assert.assertEquals("overview", access.getTabDefinition().path("id").asText());
		}
		finally {
			Context.authenticate("admin", System.getProperty("junit.password", "test"));
		}
	}
	
	@Test
	public void shouldFailClosedForUnknownPatient() {
		try {
			accessService.getAuthorizedMemberships("00000000-0000-0000-0000-000000000000");
			Assert.fail("unknown patients must fail closed");
		}
		catch (APIException e) {
			// expected
		}
	}
	
	// ========================
	// fixture helpers
	// ========================
	
	private org.openmrs.Patient createTestPatient() {
		String unique = String.valueOf(Math.abs(System.nanoTime()));
		
		// a permissive identifier type: the seeded types carry format validators
		org.openmrs.PatientIdentifierType identifierType = Context.getPatientService().getPatientIdentifierTypeByName(
		    "CAREMODELS-TEST-ID");
		if (identifierType == null) {
			identifierType = new org.openmrs.PatientIdentifierType();
			identifierType.setName("CAREMODELS-TEST-ID");
			identifierType.setDescription("care models test identifier");
			identifierType.setRequired(false);
			identifierType = Context.getPatientService().savePatientIdentifierType(identifierType);
		}
		
		org.openmrs.Patient patient = new org.openmrs.Patient();
		patient.setGender("F");
		patient.setBirthdate(new Date());
		patient.addName(new org.openmrs.PersonName("Test", unique, "Access"));
		patient.addIdentifier(new org.openmrs.PatientIdentifier(unique, identifierType, Context.getLocationService()
		        .getLocation(1)));
		return Context.getPatientService().savePatient(patient);
	}
	
	private User createUser(String username, String... privilegeNames) throws Exception {
		Role role = new Role();
		role.setRole("caremodels-" + username + "-" + System.nanoTime());
		role.setDescription("test role");
		for (String privilegeName : privilegeNames) {
			role.addPrivilege(privilege(privilegeName));
		}
		role = Context.getUserService().saveRole(role);
		
		User user = new User();
		user.setUsername(username);
		user.addRole(role);
		org.openmrs.Person person = new org.openmrs.Person();
		person.setGender("M");
		person.addName(new org.openmrs.PersonName("Test", username, "User"));
		person = Context.getPersonService().savePerson(person);
		user.setPerson(person);
		// platform 2.7: saveUser only updates; creation goes through createUser(user, password)
		return Context.getUserService().createUser(user, "Test1234!");
	}
}
