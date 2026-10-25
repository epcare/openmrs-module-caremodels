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

/**
 * Minimal comparison for the published-snapshot version format enforced by the storage envelope
 * schema ({@code MAJOR.MINOR.PATCH}, digits only). Numeric per-segment comparison, so
 * {@code 2.10.0 > 2.9.0}; pre-release suffixes are not part of the format and are rejected.
 */
public final class SemVer {
	
	private final int major;
	
	private final int minor;
	
	private final int patch;
	
	private SemVer(int major, int minor, int patch) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}
	
	/**
	 * Parses {@code MAJOR.MINOR.PATCH}. Returns null when the value does not match the format;
	 * callers decide whether that is an error or a skip.
	 */
	public static SemVer parse(String value) {
		if (value == null) {
			return null;
		}
		String[] segments = value.trim().split("\\.");
		if (segments.length != 3) {
			return null;
		}
		try {
			return new SemVer(Integer.parseInt(segments[0]), Integer.parseInt(segments[1]), Integer.parseInt(segments[2]));
		}
		catch (NumberFormatException e) {
			return null;
		}
	}
	
	/**
	 * Compares two versions in the schema's {@code MAJOR.MINOR.PATCH} format; returns negative,
	 * zero or positive when {@code a} is older, equal or newer than {@code b}.
	 * 
	 * @throws IllegalArgumentException when either version does not match the format
	 */
	public static int compare(String a, String b) {
		SemVer left = parse(a);
		SemVer right = parse(b);
		if (left == null || right == null) {
			throw new IllegalArgumentException("Versions must match MAJOR.MINOR.PATCH: '" + a + "' vs '" + b + "'");
		}
		int byMajor = Integer.compare(left.major, right.major);
		if (byMajor != 0) {
			return byMajor;
		}
		int byMinor = Integer.compare(left.minor, right.minor);
		if (byMinor != 0) {
			return byMinor;
		}
		return Integer.compare(left.patch, right.patch);
	}
}
