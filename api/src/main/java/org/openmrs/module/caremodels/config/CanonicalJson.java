/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * JCS-style canonical JSON serialization (RFC 8785 approximation) and SHA-256 checksums.
 * <p>
 * Canonical form: object keys sorted by {@link String#compareTo} (UTF-16 code unit order, the Java
 * approximation of RFC 8785's code point order), array order preserved, numbers serialized by
 * Jackson's plain writer, no whitespace. It is deterministic for a given server build, which is the
 * property the publish checksum requires (checksums are computed and verified by this same code);
 * it is not a certified RFC 8785 implementation, and checksums are therefore not portable across
 * different canonicalizers.
 */
public final class CanonicalJson {
	
	private CanonicalJson() {
	}
	
	/**
	 * @return the canonical serialization of the given tree
	 */
	public static String canonicalize(JsonNode node) {
		StringBuilder sb = new StringBuilder();
		write(node, sb);
		return sb.toString();
	}
	
	/**
	 * @return lowercase hex SHA-256 of the canonical serialization
	 */
	public static String sha256(JsonNode node) {
		return sha256Hex(canonicalize(node));
	}
	
	public static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available in this JVM", e);
		}
	}
	
	private static void write(JsonNode node, StringBuilder sb) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			sb.append("null");
		} else if (node.isObject()) {
			TreeMap<String, JsonNode> sorted = new TreeMap<String, JsonNode>();
			node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
			sb.append('{');
			boolean first = true;
			for (Map.Entry<String, JsonNode> entry : sorted.entrySet()) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				sb.append(quote(entry.getKey())).append(':');
				write(entry.getValue(), sb);
			}
			sb.append('}');
		} else if (node.isArray()) {
			sb.append('[');
			boolean first = true;
			for (JsonNode item : node) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				write(item, sb);
			}
			sb.append(']');
		} else if (node.isTextual()) {
			sb.append(quote(node.textValue()));
		} else {
			// Numbers, booleans: Jackson's writer produces deterministic plain output for these
			sb.append(node.toString());
		}
	}
	
	/**
	 * Minimal JSON string escaping: quotes, backslash and the two mandatory control characters.
	 * Other control characters pass through as-is, matching how they are stored server-side.
	 */
	private static String quote(String value) {
		StringBuilder sb = new StringBuilder(value.length() + 2);
		sb.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"':
					sb.append("\\\"");
					break;
				case '\\':
					sb.append("\\\\");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				default:
					sb.append(c);
			}
		}
		sb.append('"');
		return sb.toString();
	}
}
