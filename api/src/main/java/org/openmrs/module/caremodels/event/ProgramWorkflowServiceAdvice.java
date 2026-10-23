/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.openmrs.PatientProgram;

/** Converts successful patient-program writes into transaction-aware clinical change events. */
public class ProgramWorkflowServiceAdvice extends ClinicalChangeAdviceSupport implements MethodInterceptor {
	
	public ProgramWorkflowServiceAdvice() {
	}
	
	ProgramWorkflowServiceAdvice(ClinicalChangePublisher publisher) {
		super(publisher);
	}
	
	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		String method = invocation.getMethod().getName();
		PatientProgram argument = firstArgument(invocation);
		boolean newPatientProgram = argument != null && argument.getId() == null;
		Object result = invocation.proceed();
		PatientProgram patientProgram = result instanceof PatientProgram ? (PatientProgram) result : argument;
		if (isProgramWrite(method) && patientProgram != null && patientProgram.getPatient() != null
		        && patientProgram.getProgram() != null) {
			String reviewSourceUuid = newPatientProgram && "savePatientProgram".equals(method) ? null : patientProgram
			        .getUuid();
			publish(ClinicalChangeEvent.TriggerType.PROGRAM_CHANGED, patientProgram.getPatient().getUuid(),
			    patientProgram.getUuid(), patientProgram.getProgram().getUuid(), reviewSourceUuid);
		}
		return result;
	}
	
	private PatientProgram firstArgument(MethodInvocation invocation) {
		Object[] arguments = invocation.getArguments();
		return arguments.length > 0 && arguments[0] instanceof PatientProgram ? (PatientProgram) arguments[0] : null;
	}
	
	private boolean isProgramWrite(String method) {
		return "savePatientProgram".equals(method) || "voidPatientProgram".equals(method)
		        || "unvoidPatientProgram".equals(method);
	}
}
