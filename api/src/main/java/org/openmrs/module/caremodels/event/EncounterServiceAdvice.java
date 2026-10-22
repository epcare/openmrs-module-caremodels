/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.openmrs.Encounter;

/** Converts successful encounter writes into transaction-aware clinical change events. */
public class EncounterServiceAdvice extends ClinicalChangeAdviceSupport implements MethodInterceptor {

	public EncounterServiceAdvice() {
	}

	EncounterServiceAdvice(ClinicalChangePublisher publisher) {
		super(publisher);
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		String method = invocation.getMethod().getName();
		Encounter argument = firstArgument(invocation);
		boolean newEncounter = argument != null && argument.getId() == null;
		Object result = invocation.proceed();
		Encounter encounter = result instanceof Encounter ? (Encounter) result : argument;
		ClinicalChangeEvent.TriggerType type = triggerType(method, newEncounter);
		if (type != null && encounter != null && encounter.getPatient() != null && encounter.getEncounterType() != null) {
			String reviewSourceUuid = type == ClinicalChangeEvent.TriggerType.ENCOUNTER_SAVED ? null : encounter.getUuid();
			publish(type, encounter.getPatient().getUuid(), encounter.getUuid(), encounter.getEncounterType().getUuid(),
			    reviewSourceUuid);
		}
		return result;
	}

	private Encounter firstArgument(MethodInvocation invocation) {
		Object[] arguments = invocation.getArguments();
		return arguments.length > 0 && arguments[0] instanceof Encounter ? (Encounter) arguments[0] : null;
	}

	private ClinicalChangeEvent.TriggerType triggerType(String method, boolean newEncounter) {
		if ("saveEncounter".equals(method)) {
			return newEncounter ? ClinicalChangeEvent.TriggerType.ENCOUNTER_SAVED
			        : ClinicalChangeEvent.TriggerType.ENCOUNTER_CHANGED;
		}
		if ("voidEncounter".equals(method)) {
			return ClinicalChangeEvent.TriggerType.ENCOUNTER_VOIDED;
		}
		if ("unvoidEncounter".equals(method)) {
			return ClinicalChangeEvent.TriggerType.ENCOUNTER_CHANGED;
		}
		return null;
	}
}
