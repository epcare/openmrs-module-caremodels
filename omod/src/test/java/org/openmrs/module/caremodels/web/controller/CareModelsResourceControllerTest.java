/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.web.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.openmrs.module.caremodels.web.resource.DataRequestResource;
import org.openmrs.module.caremodels.web.resource.MembershipResource;
import org.openmrs.module.caremodels.web.resource.ModelOperationResource;
import org.openmrs.module.caremodels.web.resource.ModelResource;
import org.openmrs.module.caremodels.web.resource.WorkspaceResource;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.v1_0.controller.MainResourceController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

public class CareModelsResourceControllerTest {

	@Test
	public void shouldResolveEveryRegisteredResourceThroughCareModelsNamespace() {
		CareModelsResourceController controller = new CareModelsResourceController();
		assertTrue(MainResourceController.class.isAssignableFrom(controller.getClass()));
		assertTrue(controller.getClass().isAnnotationPresent(RestController.class));
		assertEquals("/rest/v1/caremodels", controller.getClass().getAnnotation(RequestMapping.class).value()[0]);
		Class<?>[] resources = { ModelResource.class, ModelOperationResource.class, MembershipResource.class,
		        WorkspaceResource.class, DataRequestResource.class };
		for (Class<?> resource : resources) {
			String registeredName = resource.getAnnotation(Resource.class).name();
			String resourceName = registeredName.substring(registeredName.lastIndexOf('/') + 1);
			assertEquals(registeredName, controller.buildResourceName(resourceName));
		}
	}
}
