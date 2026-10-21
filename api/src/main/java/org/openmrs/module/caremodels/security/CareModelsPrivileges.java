/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.security;

/**
 * Privileges for the Care Models module. Privileges are provisioned by the initializer module from
 * {@code omod/src/main/resources/configuration/privileges/caremodels-privileges.csv} and bundled
 * into the module roles defined in
 * {@code omod/src/main/resources/configuration/roles/caremodels-roles.csv}.
 * <ul>
 * <li>{@code View Care Models} is the master privilege: without it the Care Models menu/board is
 * hidden and no Care Models requests are made. It must never appear inside model definition JSON.</li>
 * <li>Administrative privileges govern model authoring and publication; they are separate from
 * clinical-view privileges. Each model additionally names a required view privilege in
 * {@code care_model.privilege}, granted through normal OpenMRS roles.</li>
 * </ul>
 */
public final class CareModelsPrivileges {
	
	// ========================
	// Master privilege
	// ========================
	
	/** Master privilege controlling access to the Care Models menu and patient board. */
	public static final String VIEW_CARE_MODELS = "View Care Models";
	
	// ========================
	// Administration
	// ========================
	
	/** Create, edit, import and retire Care Models. */
	public static final String MANAGE_CARE_MODELS = "Task: caremodels.model.manage";
	
	/** Validate and publish Care Model versions. */
	public static final String PUBLISH_CARE_MODELS = "Task: caremodels.model.publish";
	
	/** Perform manual enrollment transitions with an audited reason. */
	public static final String TRANSITION_CARE_MODEL_MEMBERS = "Task: caremodels.member.transition";
	
	private CareModelsPrivileges() {
	}
}
