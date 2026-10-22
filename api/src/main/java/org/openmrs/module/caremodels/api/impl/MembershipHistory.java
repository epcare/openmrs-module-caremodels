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
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.openmrs.api.APIException;
import org.openmrs.module.caremodels.model.CareModelMember;

/** Bounded audit updates preserve enrollment provenance and recorded pathway state. */
public final class MembershipHistory {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private MembershipHistory() {
	}

	public static boolean append(CareModelMember member, String version, String type, Date at, String reason,
	        String sourceUuid, String actor, boolean deduplicateSource) {
		try {
			ObjectNode root;
			if (member.getMembershipData() == null) {
				if (version == null)
					throw new APIException("Published version required for legacy membership audit");
				root = MAPPER.createObjectNode().put("schemaVersion", "1.0.0");
				root.putObject("enrollment").put("definitionVersion", version);
			} else {
				JsonNode parsed = MAPPER.readTree(member.getMembershipData());
				if (!parsed.isObject())
					throw new APIException("Invalid membership audit metadata");
				root = (ObjectNode) parsed;
			}
			JsonNode previous = root.path("history");
			if (!previous.isMissingNode() && !previous.isArray())
				throw new APIException("Invalid membership history");
			ArrayNode history = previous.isArray() ? (ArrayNode) previous : root.putArray("history");
			if (deduplicateSource)
				for (JsonNode event : history) {
					if (type.equals(event.path("type").asText()) && sourceUuid != null
					        && sourceUuid.equals(event.path("sourceUuid").asText()))
						return false;
				}
			while (history.size() >= 100)
				history.remove(0);
			ObjectNode event = history.addObject().put("type", type)
			        .put("at", java.time.Instant.ofEpochMilli(at.getTime()).toString());
			if (reason != null)
				event.put("reason", reason);
			if (sourceUuid != null)
				event.put("sourceUuid", sourceUuid);
			if (actor != null)
				event.put("actor", actor);
			member.setMembershipData(root.toString());
			return true;
		}
		catch (java.io.IOException e) {
			throw new APIException("Invalid membership audit metadata", e);
		}
	}
}
