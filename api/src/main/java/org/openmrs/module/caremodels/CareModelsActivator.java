/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels;

import org.openmrs.module.BaseModuleActivator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains the logic that is run every time this module is either started or shutdown
 */
public class CareModelsActivator extends BaseModuleActivator {
	
	private static final Logger log = LoggerFactory.getLogger(CareModelsActivator.class);
	
	/**
	 * @see BaseModuleActivator#started()
	 */
	@Override
	public void started() {
		org.openmrs.scheduler.SchedulerService scheduler = org.openmrs.api.context.Context.getSchedulerService();
		org.openmrs.scheduler.TaskDefinition task = scheduler.getTaskByName("Care Models enrollment reconciliation");
		if (task == null) {
			task = new org.openmrs.scheduler.TaskDefinition();
			task.setName("Care Models enrollment reconciliation");
			task.setDescription("Bounded missed-event recovery using existing core global-property checkpoint");
			task.setTaskClass("org.openmrs.module.caremodels.task.CareModelsReconciliationTask");
			task.setStartOnStartup(true);
			task.setRepeatInterval(60L);
			task.setStartTime(new java.util.Date(System.currentTimeMillis() + 60000));
			scheduler.saveTaskDefinition(task);
		}
		if (Boolean.TRUE.equals(task.getStartOnStartup()))
			scheduler.scheduleIfNotRunning(task);
		importShippedCareModels();
		log.info("Started Care Models");
	}
	
	/**
	 * Imports shipped care model definitions from {app data dir}/configuration/caremodel/ when
	 * present. Failures are logged and isolated per file; a broken content pack can never prevent
	 * module startup.
	 */
	private void importShippedCareModels() {
		try {
			new org.openmrs.module.caremodels.config.CareModelConfigImporter(
			        org.openmrs.api.context.Context.getService(org.openmrs.module.caremodels.api.CareModelService.class))
			        .importShippedModels();
		}
		catch (Exception e) {
			log.error("Unable to run the shipped care model configuration import", e);
		}
	}
	
	/**
	 * @see BaseModuleActivator#stopped()
	 */
	@Override
	public void stopped() {
		org.openmrs.scheduler.SchedulerService scheduler = org.openmrs.api.context.Context.getSchedulerService();
		org.openmrs.scheduler.TaskDefinition task = scheduler.getTaskByName("Care Models enrollment reconciliation");
		if (task != null) {
			try {
				scheduler.shutdownTask(task);
			}
			catch (org.openmrs.scheduler.SchedulerException e) {
				log.warn("Unable to stop Care Models reconciliation task");
			}
		}
		log.info("Shutdown Care Models");
	}
	
}
