/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Delivers clinical changes after commit, or immediately when the service transaction has already
 * completed.
 */
public class ClinicalChangePublisher {

	private static final Logger log = Logger.getLogger(ClinicalChangePublisher.class.getName());

	private List<ClinicalChangeListener> listeners = Collections.emptyList();

	public void setListeners(List<ClinicalChangeListener> listeners) {
		this.listeners = listeners == null ? Collections.<ClinicalChangeListener> emptyList() : Collections
		        .unmodifiableList(new ArrayList<ClinicalChangeListener>(listeners));
	}

	public void publishAfterCommit(final ClinicalChangeEvent event) {
		if (TransactionSynchronizationManager.isSynchronizationActive()
		        && TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {

				@Override
				public void afterCommit() {
					deliver(event);
				}
			});
		} else {
			deliver(event);
		}
	}

	private void deliver(ClinicalChangeEvent event) {
		for (ClinicalChangeListener listener : listeners) {
			try {
				listener.onClinicalChange(event);
			}
			catch (RuntimeException e) {
				log.log(Level.SEVERE, "Clinical change listener failed ({0}); reconciliation required", e.getClass()
				        .getSimpleName());
			}
		}
	}
}
