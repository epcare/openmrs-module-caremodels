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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registry of the widget types, action handlers, data source types and result types this build
 * supports. Only registered extensions are usable: definitions may never introduce arbitrary
 * component types, resolver ids or executable code.
 * <p>
 * The enum values in the JSON Schemas must stay in sync with these sets;
 * {@code RegistrySchemaSyncTest} asserts that.
 */
public final class Registry {
	
	// ========================
	// Widget types (registered render components)
	// ========================
	
	public static final String WIDGET_METRIC = "metric";
	
	public static final String WIDGET_TREND_CHART = "trend-chart";
	
	public static final String WIDGET_CLINICAL_TABLE = "clinical-table";
	
	public static final String WIDGET_TIMELINE = "timeline";
	
	public static final String WIDGET_FORM_LAUNCHER = "form-launcher";
	
	public static final String WIDGET_ACTION_PANEL = "action-panel";
	
	// ========================
	// Action handler types (allowlisted)
	// ========================
	
	public static final String ACTION_LAUNCH_FORM = "launch-form";
	
	public static final String ACTION_NAVIGATE_INTERNAL = "navigate-internal";
	
	public static final String ACTION_OPEN_ORDER_ENTRY = "open-order-entry";
	
	public static final String ACTION_OPEN_APPOINTMENTS = "open-appointments";
	
	public static final String ACTION_ENROLLMENT_TRANSITION = "enrollment-transition";
	
	// ========================
	// Data source types
	// ========================
	
	public static final String DS_LATEST_OBSERVATION = "latest-observation";
	
	public static final String DS_HISTORICAL_OBSERVATIONS = "historical-observations";
	
	public static final String DS_PROGRAM_STATE = "program-state";
	
	public static final String DS_ENCOUNTER_QUERY = "encounter-query";
	
	public static final String DS_ORDER_QUERY = "order-query";
	
	public static final String DS_REGISTERED = "registered";
	
	// ========================
	// Result types
	// ========================
	
	public static final Set<String> RESULT_TYPES = unmodifiable("NUMBER", "CODED", "DATE", "STRING", "TIMESERIES", "TABLE");
	
	public static final Set<String> WIDGET_TYPES = unmodifiable(WIDGET_METRIC, WIDGET_TREND_CHART, WIDGET_CLINICAL_TABLE,
	    WIDGET_TIMELINE, WIDGET_FORM_LAUNCHER, WIDGET_ACTION_PANEL);
	
	public static final Set<String> ACTION_TYPES = unmodifiable(ACTION_LAUNCH_FORM, ACTION_NAVIGATE_INTERNAL,
	    ACTION_OPEN_ORDER_ENTRY, ACTION_OPEN_APPOINTMENTS, ACTION_ENROLLMENT_TRANSITION);
	
	public static final Set<String> DATA_SOURCE_TYPES = unmodifiable(DS_LATEST_OBSERVATION, DS_HISTORICAL_OBSERVATIONS,
	    DS_PROGRAM_STATE, DS_ENCOUNTER_QUERY, DS_ORDER_QUERY, DS_REGISTERED);
	
	private static Set<String> unmodifiable(String... values) {
		Set<String> set = new LinkedHashSet<String>();
		Collections.addAll(set, values);
		return Collections.unmodifiableSet(set);
	}
	
	private Registry() {
	}
}
