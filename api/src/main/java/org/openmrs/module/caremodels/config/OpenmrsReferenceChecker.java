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

import org.openmrs.Concept;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Program;
import org.openmrs.api.context.Context;

/**
 * OpenMRS-backed existence and referent-type checks for terminology aliases. Kept as an interface
 * so the semantic validator is unit-testable without a database; the production implementation
 * delegates to the OpenMRS API and must run inside an authenticated context.
 */
public interface OpenmrsReferenceChecker {
	
	boolean conceptExists(String uuid);
	
	boolean programExists(String uuid);
	
	boolean encounterTypeExists(String uuid);
	
	boolean formExists(String uuid);
	
	boolean privilegeExists(String name);
	
	/**
	 * Default production implementation backed by the OpenMRS API.
	 */
	class OpenmrsApiReferenceChecker implements OpenmrsReferenceChecker {
		
		@Override
		public boolean conceptExists(String uuid) {
			Concept concept = Context.getConceptService().getConceptByUuid(uuid);
			return concept != null && !concept.isRetired();
		}
		
		@Override
		public boolean programExists(String uuid) {
			Program program = Context.getProgramWorkflowService().getProgramByUuid(uuid);
			return program != null && !program.isRetired();
		}
		
		@Override
		public boolean encounterTypeExists(String uuid) {
			EncounterType encounterType = Context.getEncounterService().getEncounterTypeByUuid(uuid);
			return encounterType != null && !encounterType.isRetired();
		}
		
		@Override
		public boolean formExists(String uuid) {
			Form form = Context.getFormService().getFormByUuid(uuid);
			return form != null && !form.isRetired();
		}
		
		@Override
		public boolean privilegeExists(String name) {
			return Context.getUserService().getPrivilege(name) != null;
		}
	}
}
