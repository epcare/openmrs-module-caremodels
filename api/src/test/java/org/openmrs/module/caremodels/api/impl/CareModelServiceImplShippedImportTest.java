/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.api.impl;

import java.util.Date;

import org.junit.Test;
import org.openmrs.module.caremodels.config.StorageEnvelope;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The shipped-import version gate: internal improvements published at an equal or higher version
 * must never be overwritten by a content pack.
 */
public class CareModelServiceImplShippedImportTest {
	
	@Test
	public void shippedSnapshotShouldBeNewerThanNeverPublishedModel() {
		assertTrue(CareModelServiceImpl.shippedIsNewer(published("1.0.0"), envelopeWithoutPublished()));
	}
	
	@Test
	public void strictlyNewerShippedVersionShouldApply() {
		assertTrue(CareModelServiceImpl.shippedIsNewer(published("2.0.0"), published("1.9.9")));
		assertTrue(CareModelServiceImpl.shippedIsNewer(published("1.10.0"), published("1.9.0")));
	}
	
	@Test
	public void equalOrOlderShippedVersionShouldNeverApply() {
		assertFalse(CareModelServiceImpl.shippedIsNewer(published("1.2.0"), published("1.2.0")));
		assertFalse(CareModelServiceImpl.shippedIsNewer(published("1.1.9"), published("1.2.0")));
		assertFalse(CareModelServiceImpl.shippedIsNewer(published("0.9.0"), published("1.0.0")));
	}
	
	@Test
	public void draftOnlyShippedEnvelopeShouldNeverApplyToExistingModel() {
		assertFalse(CareModelServiceImpl.shippedIsNewer(envelopeWithoutPublished(), published("0.0.1")));
		assertFalse(CareModelServiceImpl.shippedIsNewer(envelopeWithoutPublished(), envelopeWithoutPublished()));
	}
	
	@Test
	public void emptyInputsShouldNeverApplyExceptMissingStoredEnvelope() {
		assertFalse(CareModelServiceImpl.shippedIsNewer(null, published("0.0.1")));
		// no stored envelope means nothing internally improved to protect
		assertTrue(CareModelServiceImpl.shippedIsNewer(published("1.0.0"), null));
	}
	
	private static StorageEnvelope published(String version) {
		StorageEnvelope envelope = new StorageEnvelope();
		StorageEnvelope.PublishedSnapshot snapshot = new StorageEnvelope.PublishedSnapshot();
		snapshot.setVersion(version);
		snapshot.setPublishedAt(new Date());
		envelope.setPublished(snapshot);
		return envelope;
	}
	
	private static StorageEnvelope envelopeWithoutPublished() {
		return new StorageEnvelope();
	}
}
