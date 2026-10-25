/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.task;

import java.util.concurrent.atomic.AtomicBoolean;

import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.CareModelReconciliationService;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenMRS executes scheduled tasks in its daemon context; no clinical user impersonation. Each run
 * commits one bounded recovery batch; a failed batch keeps its checkpoint so the run retries the
 * same page. The guard prevents overlap when a batch outlasts the repeat interval.
 */
public class CareModelsReconciliationTask extends AbstractTask {
	
	private static final Logger log = LoggerFactory.getLogger(CareModelsReconciliationTask.class);
	
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
		catch (NumberFormatException e) {
			log.warn("Invalid caremodels.reconciliation.batchSize ('{}'); fix the global property", configured());
		}
		catch (RuntimeException e) {
			log.warn("Care Models recovery batch failed; checkpoint retained for retry", e);
		}
		finally {
			running.set(false);
		}
	}
	
	private static String configured() {
		try {
			return Context.getAdministrationService().getGlobalProperty("caremodels.reconciliation.batchSize", "50");
		}
		catch (RuntimeException e) {
			return "<unavailable>";
		}
	}
}
