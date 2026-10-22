/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openmrs.module.caremodels.config.StorageEnvelope.PublishedSnapshot;

/** Validates integrity guarantees that JSON Schema cannot express. */
public final class StorageEnvelopeValidator {

	private StorageEnvelopeValidator() {
	}

	public static ValidationResult validate(StorageEnvelope envelope) {
		ValidationResult result = ValidationResult.valid();
		Set<String> versions = new HashSet<String>();
		validateSnapshot(envelope.getPublished(), "$.published", versions, result);
		List<PublishedSnapshot> history = envelope.getHistory();
		if (history != null) {
			for (int i = 0; i < history.size(); i++) {
				validateSnapshot(history.get(i), "$.history[" + i + "]", versions, result);
			}
		}
		if (envelope.getDraft() != null && envelope.getPublished() != null
		        && !envelope.getPublished().getVersion().equals(envelope.getDraft().getBasedOnVersion())) {
			result.addError("$.draft.basedOnVersion", "Draft must be based on the current published version");
		}
		int minimumRevision = envelope.getDraft() == null ? 0 : 1;
		if (envelope.getPublished() != null) {
			minimumRevision = Math.max(minimumRevision, 2 + (history == null ? 0 : history.size()));
		}
		if (envelope.getRevision() < minimumRevision) {
			result.addError("$.revision", "Revision is inconsistent with the stored draft and published history");
		}
		return result;
	}

	private static void validateSnapshot(PublishedSnapshot snapshot, String path, Set<String> versions,
	        ValidationResult result) {
		if (snapshot == null) {
			return;
		}
		if (!versions.add(snapshot.getVersion())) {
			result.addError(path + ".version", "Published snapshot versions must be unique");
		}
		String calculated = CanonicalJson.sha256(snapshot.getDefinition());
		if (!calculated.equals(snapshot.getChecksum())) {
			result.addError(path + ".checksum", "Checksum does not match the canonical definition");
		}
	}
}
