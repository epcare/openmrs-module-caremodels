/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class ClinicalChangePublisherTest {

	private final List<ClinicalChangeEvent> received = new CopyOnWriteArrayList<ClinicalChangeEvent>();

	@After
	public void clearTransactionState() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
		TransactionSynchronizationManager.setActualTransactionActive(false);
	}

	@Test
	public void shouldPublishImmediatelyWhenServiceTransactionAlreadyCompleted() {
		ClinicalChangePublisher publisher = publisher(new ClinicalChangeListener() {

			@Override
			public void onClinicalChange(ClinicalChangeEvent event) {
				received.add(event);
			}
		});
		publisher.publishAfterCommit(event());
		assertEquals(1, received.size());
	}

	@Test
	public void shouldPublishOnlyAfterActiveTransactionCommits() {
		ClinicalChangePublisher publisher = publisher(new ClinicalChangeListener() {

			@Override
			public void onClinicalChange(ClinicalChangeEvent event) {
				received.add(event);
			}
		});
		TransactionSynchronizationManager.initSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(true);
		publisher.publishAfterCommit(event());
		assertTrue(received.isEmpty());

		for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
			synchronization.afterCommit();
		}
		assertEquals(1, received.size());
	}

	@Test
	public void shouldNotPublishWhenTransactionDoesNotCommit() {
		ClinicalChangePublisher publisher = publisher(new ClinicalChangeListener() {

			@Override
			public void onClinicalChange(ClinicalChangeEvent event) {
				received.add(event);
			}
		});
		TransactionSynchronizationManager.initSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(true);
		publisher.publishAfterCommit(event());
		for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
			synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
		}
		assertTrue(received.isEmpty());
	}

	@Test
	public void shouldIsolateListenerFailures() {
		ClinicalChangePublisher publisher = publisher(new ClinicalChangeListener() {

			@Override
			public void onClinicalChange(ClinicalChangeEvent event) {
				throw new IllegalStateException("broken listener");
			}
		}, new ClinicalChangeListener() {

			@Override
			public void onClinicalChange(ClinicalChangeEvent event) {
				received.add(event);
			}
		});
		publisher.publishAfterCommit(event());
		assertEquals(1, received.size());
	}

	private ClinicalChangePublisher publisher(ClinicalChangeListener... listeners) {
		ClinicalChangePublisher publisher = new ClinicalChangePublisher();
		publisher.setListeners(listeners.length == 0 ? Collections.<ClinicalChangeListener> emptyList() : Arrays
		        .asList(listeners));
		return publisher;
	}

	private ClinicalChangeEvent event() {
		return new ClinicalChangeEvent(ClinicalChangeEvent.TriggerType.OBSERVATION_SAVED, "patient", "obs", "concept",
		        new Date());
	}
}
