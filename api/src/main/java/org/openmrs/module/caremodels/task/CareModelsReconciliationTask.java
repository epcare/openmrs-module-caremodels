/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.task;

import java.util.concurrent.atomic.AtomicBoolean;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.CareModelReconciliationService;
import org.openmrs.scheduler.tasks.AbstractTask;

/** OpenMRS executes scheduled tasks in its daemon context; no clinical user impersonation. */
public class CareModelsReconciliationTask extends AbstractTask {

	private static final java.util.logging.Logger LOG = java.util.logging.Logger
	        .getLogger(CareModelsReconciliationTask.class.getName());

	private final AtomicBoolean running = new AtomicBoolean();

	@Override
	public void execute() {
		if (!running.compareAndSet(false, true))
			return;
		try {
			if (!Boolean.parseBoolean(Context.getAdministrationService().getGlobalProperty(
			    "caremodels.reconciliation.enabled", "true")))
				return;
			String configured = Context.getAdministrationService().getGlobalProperty("caremodels.reconciliation.batchSize",
			    "50");
			int limit = Integer.parseInt(configured);
			Context.getService(CareModelReconciliationService.class).reconcileNextBatch(limit);
		}
		catch (RuntimeException e) {
			LOG.log(java.util.logging.Level.WARNING,
			    "Care Models recovery batch failed ({0}); checkpoint retained for retry", e.getClass().getSimpleName());
		}
		finally {
			running.set(false);
		}
	}
}
