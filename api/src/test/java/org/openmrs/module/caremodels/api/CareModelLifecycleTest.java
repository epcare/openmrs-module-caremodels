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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Privilege;
import org.openmrs.api.context.Context;
import org.openmrs.api.APIException;
import org.openmrs.module.caremodels.config.CanonicalJson;
import org.openmrs.module.caremodels.config.StaleRevisionException;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.config.ValidationResult;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;
import org.openmrs.test.BaseModuleContextSensitiveTest;

/**
 * Integration tests for the W2 model lifecycle against the module database (H2 with the module's
 * Liquibase changelog): draft/publish CAS, MOD-007 privilege gate, duplicate, import/export and
 * status transitions.
 */
public class CareModelLifecycleTest extends BaseModuleContextSensitiveTest {
	
	private CareModelService service;
	
	private ObjectMapper mapper;
	
	private String testPrivilege;
	
	@Before
	public void setup() throws Exception {
		service = Context.getService(CareModelService.class);
		mapper = new ObjectMapper();
		
		// a privilege registered in the local OpenMRS instance for MOD-007 checks
		testPrivilege = "Task: caremodels.lifecycle.test";
		if (Context.getUserService().getPrivilege(testPrivilege) == null) {
			Context.getUserService().savePrivilege(new Privilege(testPrivilege, "lifecycle test privilege"));
		}
	}
	
	private String minimalDefinition(String label) {
		ObjectNode definition = mapper.createObjectNode();
		definition.put("schemaVersion", "1.0.0");
		
		ObjectNode metadata = definition.putObject("metadata");
		metadata.put("category", "SUPPORT");
		metadata.putObject("display").put("label", label).put("order", 99);
		
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
	
	@Test
	public void createCareModel_shouldCreateDraftModelWithEnvelope() {
		CareModel model = service.createCareModel("LIFECYCLE_T1", "Lifecycle T1", "test", testPrivilege,
		    minimalDefinition("T1"));
		
		Assert.assertNotNull(model.getId());
		Assert.assertEquals(CareModelStatus.DRAFT, model.getStatus());
		Assert.assertEquals(Integer.valueOf(1), model.getRevision());
		
		StorageEnvelope envelope = StorageEnvelope.fromJson(new ObjectMapper(), model.getConfiguration());
		Assert.assertNotNull(envelope.getDraft());
		Assert.assertNull(envelope.getPublished());
	}
	
	@Test
	public void createCareModel_shouldRejectDuplicateCode() {
		service.createCareModel("LIFECYCLE_T2", "Lifecycle T2", null, testPrivilege, minimalDefinition("T2"));
		try {
			service.createCareModel("LIFECYCLE_T2", "Duplicate", null, testPrivilege, minimalDefinition("T2"));
			Assert.fail("duplicate code should be rejected");
		}
		catch (APIException e) {
			Assert.assertTrue(e.getMessage().contains("already exists"));
		}
	}
	
	@Test
	public void createCareModel_shouldRejectInvalidDefinition() {
		String invalid = minimalDefinition("bad");
		// an unregistered widget type makes the definition structurally invalid
		invalid = invalid.replace("\"widgets\":[]", "\"widgets\":[{\"id\":\"x\",\"type\":\"bogus\"}]");
		try {
			service.createCareModel("LIFECYCLE_T3", "Lifecycle T3", null, testPrivilege, invalid);
			Assert.fail("invalid definition should be rejected");
		}
		catch (APIException e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("invalid"));
		}
	}
	
	@Test
	public void createCareModel_shouldRejectBlankPrivilege() {
		try {
			service.createCareModel("LIFECYCLE_T4", "Lifecycle T4", null, "  ", minimalDefinition("T4"));
			Assert.fail("blank privilege should be rejected");
		}
		catch (APIException e) {
			// fail closed
		}
	}
	
	@Test
	public void saveDraft_shouldBumpRevisionAndRejectStaleWrites() {
		service.createCareModel("LIFECYCLE_T5", "Lifecycle T5", null, testPrivilege, minimalDefinition("T5"));
		
		service.saveDraft("LIFECYCLE_T5", minimalDefinition("T5 v2"), 1);
		CareModel model = service.getCareModelByCode("LIFECYCLE_T5");
		Assert.assertEquals(Integer.valueOf(2), model.getRevision());
		
		// stale writer still believes revision 1
		try {
			service.saveDraft("LIFECYCLE_T5", minimalDefinition("stale"), 1);
			Assert.fail("stale draft write should be rejected");
		}
		catch (StaleRevisionException e) {
			Assert.assertEquals(Integer.valueOf(1), e.getExpectedRevision());
			Assert.assertEquals(Integer.valueOf(2), e.getCurrentRevision());
		}
	}
	
	@Test
	public void publishCareModel_shouldPublishDraftAndChecksumDefinition() throws Exception {
		String definition = minimalDefinition("P1");
		service.createCareModel("LIFECYCLE_P1", "Lifecycle P1", null, testPrivilege, definition);
		
		service.publishCareModel("LIFECYCLE_P1", "1.0.0", 1);
		
		CareModel model = service.getCareModelByCode("LIFECYCLE_P1");
		Assert.assertEquals(CareModelStatus.PUBLISHED, model.getStatus());
		Assert.assertEquals(Integer.valueOf(2), model.getRevision());
		
		StorageEnvelope envelope = StorageEnvelope.fromJson(new ObjectMapper(), model.getConfiguration());
		Assert.assertNotNull(envelope.getPublished());
		Assert.assertEquals("1.0.0", envelope.getPublished().getVersion());
		Assert.assertEquals(CanonicalJson.sha256(new ObjectMapper().readTree(definition)), envelope.getPublished()
		        .getChecksum());
		Assert.assertEquals(definition, service.getPublishedDefinition("LIFECYCLE_P1"));
		
		// second publish archives the first snapshot
		service.saveDraft("LIFECYCLE_P1", minimalDefinition("P1 v2"), 2);
		service.publishCareModel("LIFECYCLE_P1", "1.1.0", 3);
		envelope = StorageEnvelope.fromJson(new ObjectMapper(), service.getCareModelByCode("LIFECYCLE_P1")
		        .getConfiguration());
		Assert.assertEquals(1, envelope.getHistory().size());
		Assert.assertEquals("1.0.0", envelope.getHistory().get(0).getVersion());
	}
	
	@Test
	public void publishCareModel_shouldRejectPublishWhenPrivilegeUnregistered() {
		service.createCareModel("LIFECYCLE_P2", "Lifecycle P2", null, "NoSuchPrivilege: definitely_missing",
		    minimalDefinition("P2"));
		try {
			service.publishCareModel("LIFECYCLE_P2", "1.0.0", 1);
			Assert.fail("publish with unregistered privilege should be rejected (MOD-007)");
		}
		catch (APIException e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("not registered"));
		}
		// the model must remain unpublished
		Assert.assertEquals(CareModelStatus.DRAFT, service.getCareModelByCode("LIFECYCLE_P2").getStatus());
	}
	
	@Test
	public void publishCareModel_shouldRejectStaleRevision() {
		service.createCareModel("LIFECYCLE_P4", "Lifecycle P4", null, testPrivilege, minimalDefinition("P4"));
		try {
			service.publishCareModel("LIFECYCLE_P4", "1.0.0", 99);
			Assert.fail("stale publish should be rejected");
		}
		catch (StaleRevisionException e) {
			// CAS held
		}
	}
	
	@Test
	public void duplicateFromPublished_shouldCreateEditableDraft() throws Exception {
		String definition = minimalDefinition("D1");
		service.createCareModel("LIFECYCLE_D1", "Lifecycle D1", null, testPrivilege, definition);
		service.publishCareModel("LIFECYCLE_D1", "1.0.0", 1);
		
		CareModel duplicate = service.duplicateFromPublished("LIFECYCLE_D1", "LIFECYCLE_D1_COPY", "Copy of D1",
		    testPrivilege);
		
		Assert.assertEquals(CareModelStatus.DRAFT, duplicate.getStatus());
		Assert.assertNull(service.getPublishedDefinition("LIFECYCLE_D1_COPY"));
		ObjectNode duplicateEnvelope = (ObjectNode) mapper.readTree(service.getEnvelope("LIFECYCLE_D1_COPY"));
		Assert.assertEquals(definition, duplicateEnvelope.path("draft").path("definition").toString());
	}
	
	@Test
	public void importModel_shouldRejectEnvelopeWithUnresolvableReference() throws Exception {
		// publish a model locally, then tamper its envelope to reference a concept that does not exist
		service.createCareModel("LIFECYCLE_I1", "Lifecycle I1", null, testPrivilege, minimalDefinition("I1"));
		service.publishCareModel("LIFECYCLE_I1", "1.0.0", 1);
		String envelopeJson = service.getEnvelope("LIFECYCLE_I1");
		
		ObjectNode envelope = (ObjectNode) mapper.readTree(envelopeJson);
		((ObjectNode) envelope.path("published").path("definition").path("terminology")).putObject("concepts").put(
		    "missing_concept", "00000000-0000-0000-0000-000000000000");
		
		try {
			service.importModel("LIFECYCLE_I1_IMPORT", "Imported", testPrivilege, envelope.toString());
			Assert.fail("import with unresolvable reference should be rejected");
		}
		catch (APIException e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("invalid"));
		}
	}
	
	@Test
	public void importModel_shouldCreatePublishedModelFromValidEnvelope() throws Exception {
		service.createCareModel("LIFECYCLE_I2", "Lifecycle I2", null, testPrivilege, minimalDefinition("I2"));
		service.publishCareModel("LIFECYCLE_I2", "2.0.0", 1);
		String envelopeJson = service.getEnvelope("LIFECYCLE_I2");
		
		CareModel imported = service.importModel("LIFECYCLE_I2_COPY", "Imported I2", testPrivilege, envelopeJson);
		
		Assert.assertEquals(CareModelStatus.PUBLISHED, imported.getStatus());
		Assert.assertEquals(service.getPublishedDefinition("LIFECYCLE_I2"),
		    service.getPublishedDefinition("LIFECYCLE_I2_COPY"));
	}
	
	@Test
	public void deactivateAndReactivate_shouldTransitionStatus() {
		service.createCareModel("LIFECYCLE_S1", "Lifecycle S1", null, testPrivilege, minimalDefinition("S1"));
		service.publishCareModel("LIFECYCLE_S1", "1.0.0", 1);
		
		service.deactivateCareModel("LIFECYCLE_S1");
		Assert.assertEquals(CareModelStatus.INACTIVE, service.getCareModelByCode("LIFECYCLE_S1").getStatus());
		
		service.reactivateCareModel("LIFECYCLE_S1");
		Assert.assertEquals(CareModelStatus.PUBLISHED, service.getCareModelByCode("LIFECYCLE_S1").getStatus());
	}
	
	@Test
	public void deactivateCareModel_shouldRejectNeverPublishedModel() {
		service.createCareModel("LIFECYCLE_S2", "Lifecycle S2", null, testPrivilege, minimalDefinition("S2"));
		try {
			service.deactivateCareModel("LIFECYCLE_S2");
			Assert.fail("deactivating a never-published model should be rejected");
		}
		catch (APIException e) {
			// expected
		}
	}
	
	@Test
	public void validateDraft_shouldReportErrorsWithoutPersisting() {
		String invalid = minimalDefinition("bad").replace("\"schemaVersion\":\"1.0.0\"", "\"schemaVersion\":\"9.9.9\"");
		ValidationResult result = service.validateDraft(invalid);
		Assert.assertFalse(result.isValid());
		Assert.assertTrue(result.toString().contains("schemaVersion"));
	}
}
