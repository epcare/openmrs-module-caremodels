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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.event.ClinicalChangeListener;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;
import org.openmrs.test.BaseModuleContextSensitiveTest;

public class CareModelServiceTest extends BaseModuleContextSensitiveTest {

	private CareModelService service;

	@Before
	public void setup() throws Exception {
		service = Context.getService(CareModelService.class);
	}

	@Test
	public void shouldLoadCareModelService() {
		Assert.assertNotNull(service);
		Assert.assertNotNull(Context.getService(CareModelMemberService.class));
		Assert.assertNotNull(Context.getService(CareModelEnrollmentService.class));
		Assert.assertNotNull(Context.getRegisteredComponent("caremodels.ClinicalChangeEnrollmentProcessor",
		    ClinicalChangeListener.class));
	}

	@Test(expected = Exception.class)
	public void saveCareModel_shouldRejectModelWithoutPrivilege() {
		CareModel model = new CareModel();
		model.setName("Test Model");
		model.setCode("TEST_MODEL");
		model.setStatus(CareModelStatus.DRAFT);
		model.setConfiguration("{\"storageSchemaVersion\":\"1.0.0\"}");

		service.saveCareModel(model);
	}

	@Test(expected = Exception.class)
	public void saveCareModel_shouldRejectModelWithoutCode() {
		CareModel model = new CareModel();
		model.setName("Test Model");
		model.setPrivilege("View Care Models");
		model.setStatus(CareModelStatus.DRAFT);
		model.setConfiguration("{\"storageSchemaVersion\":\"1.0.0\"}");

		service.saveCareModel(model);
	}
}
