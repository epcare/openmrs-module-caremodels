/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.api.db.hibernate.HibernateUtil;

/** Converts successful observation writes into transaction-aware clinical change events. */
public class ObsServiceAdvice extends ClinicalChangeAdviceSupport implements MethodInterceptor {

	public ObsServiceAdvice() {
	}

	ObsServiceAdvice(ClinicalChangePublisher publisher) {
		super(publisher);
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		String method = invocation.getMethod().getName();
		Obs argument = firstArgument(invocation);
		boolean newObservation = argument != null && argument.getId() == null && argument.getPreviousVersion() == null;
		Object result = invocation.proceed();
		Obs obs = result instanceof Obs ? (Obs) result : argument;
		ClinicalChangeEvent.TriggerType type = triggerType(method, newObservation);
		Patient patient = patient(obs);
		if (type != null && obs != null && patient != null && obs.getConcept() != null) {
			publish(type, patient.getUuid(), obs.getUuid(), obs.getConcept().getUuid());
		}
		return result;
	}

	private Obs firstArgument(MethodInvocation invocation) {
		Object[] arguments = invocation.getArguments();
		return arguments.length > 0 && arguments[0] instanceof Obs ? (Obs) arguments[0] : null;
	}

	private ClinicalChangeEvent.TriggerType triggerType(String method, boolean newObservation) {
		if ("saveObs".equals(method)) {
			return newObservation ? ClinicalChangeEvent.TriggerType.OBSERVATION_SAVED
			        : ClinicalChangeEvent.TriggerType.OBSERVATION_CHANGED;
		}
		if ("voidObs".equals(method)) {
			return ClinicalChangeEvent.TriggerType.OBSERVATION_VOIDED;
		}
		if ("unvoidObs".equals(method)) {
			return ClinicalChangeEvent.TriggerType.OBSERVATION_CHANGED;
		}
		return null;
	}

	private Patient patient(Obs obs) {
		if (obs == null) {
			return null;
		}
		if (obs.getEncounter() != null && obs.getEncounter().getPatient() != null) {
			return obs.getEncounter().getPatient();
		}
		if (obs.getPerson() != null) {
			Person person = HibernateUtil.getRealObjectFromProxy(obs.getPerson());
			return person instanceof Patient ? (Patient) person : null;
		}
		return null;
	}
}
