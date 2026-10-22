/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientProgram;
import org.openmrs.Program;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.ProgramWorkflowService;

public class ClinicalServiceAdviceTest {

	private final List<ClinicalChangeEvent> received = new CopyOnWriteArrayList<ClinicalChangeEvent>();

	private final ClinicalChangePublisher publisher = publisher();

	@Test
	public void shouldClassifyNewEncounterAfterSuccessfulSave() throws Throwable {
		Encounter encounter = encounter();
		Method method = EncounterService.class.getMethod("saveEncounter", Encounter.class);
		new EncounterServiceAdvice(publisher).invoke(invocation(method, new Object[] { encounter }, encounter));
		assertEvent(ClinicalChangeEvent.TriggerType.ENCOUNTER_SAVED, "encounter", "encounter-type", null);
	}

	@Test
	public void shouldClassifyExistingEncounterAndUnvoidAsChanged() throws Throwable {
		Encounter encounter = encounter();
		encounter.setEncounterId(12);
		Method save = EncounterService.class.getMethod("saveEncounter", Encounter.class);
		new EncounterServiceAdvice(publisher).invoke(invocation(save, new Object[] { encounter }, encounter));
		assertEvent(ClinicalChangeEvent.TriggerType.ENCOUNTER_CHANGED, "encounter", "encounter-type", "encounter");
	}

	@Test
	public void shouldClassifyObservationCorrectionAsChanged() throws Throwable {
		Obs previous = observation();
		previous.setObsId(8);
		previous.setUuid("original-obs");
		Obs correction = observation();
		correction.setUuid("replacement-obs");
		correction.setPreviousVersion(previous);
		Method method = ObsService.class.getMethod("saveObs", Obs.class, String.class);
		new ObsServiceAdvice(publisher).invoke(invocation(method, new Object[] { correction, "correction" }, correction));
		assertEvent(ClinicalChangeEvent.TriggerType.OBSERVATION_CHANGED, "replacement-obs", "concept", "original-obs");
	}

	@Test
	public void shouldPublishProgramChangesForVoidOperations() throws Throwable {
		PatientProgram patientProgram = patientProgram();
		Method method = ProgramWorkflowService.class.getMethod("voidPatientProgram", PatientProgram.class, String.class);
		new ProgramWorkflowServiceAdvice(publisher).invoke(invocation(method, new Object[] { patientProgram,
		        "entered in error" }, patientProgram));
		assertEvent(ClinicalChangeEvent.TriggerType.PROGRAM_CHANGED, "patient-program", "program", "patient-program");
	}

	@Test(expected = IllegalStateException.class)
	public void shouldNotPublishWhenServiceWriteFails() throws Throwable {
		Encounter encounter = encounter();
		Method method = EncounterService.class.getMethod("saveEncounter", Encounter.class);
		try {
			new EncounterServiceAdvice(publisher).invoke(failingInvocation(method, new Object[] { encounter }));
		}
		finally {
			assertTrue(received.isEmpty());
		}
	}

	private void assertEvent(ClinicalChangeEvent.TriggerType type, String sourceUuid, String referenceUuid,
	        String reviewSourceUuid) {
		assertEquals(1, received.size());
		ClinicalChangeEvent event = received.remove(0);
		assertEquals(type, event.getTriggerType());
		assertEquals("patient", event.getPatientUuid());
		assertEquals(sourceUuid, event.getSourceUuid());
		assertEquals(referenceUuid, event.getReferenceUuid());
		assertEquals(reviewSourceUuid, event.getReviewSourceUuid());
	}

	private ClinicalChangePublisher publisher() {
		ClinicalChangePublisher result = new ClinicalChangePublisher();
		result.setListeners(Arrays.asList(new ClinicalChangeListener() {

			@Override
			public void onClinicalChange(ClinicalChangeEvent event) {
				received.add(event);
			}
		}));
		return result;
	}

	private Encounter encounter() {
		Patient patient = new Patient();
		patient.setUuid("patient");
		EncounterType type = new EncounterType();
		type.setUuid("encounter-type");
		Encounter encounter = new Encounter();
		encounter.setUuid("encounter");
		encounter.setPatient(patient);
		encounter.setEncounterType(type);
		return encounter;
	}

	private Obs observation() {
		Patient patient = new Patient();
		patient.setUuid("patient");
		Concept concept = new Concept();
		concept.setUuid("concept");
		Obs obs = new Obs();
		obs.setUuid("obs");
		obs.setPerson(patient);
		obs.setConcept(concept);
		obs.setObsDatetime(new Date());
		return obs;
	}

	private PatientProgram patientProgram() {
		Patient patient = new Patient();
		patient.setUuid("patient");
		Program program = new Program();
		program.setUuid("program");
		PatientProgram patientProgram = new PatientProgram();
		patientProgram.setUuid("patient-program");
		patientProgram.setPatient(patient);
		patientProgram.setProgram(program);
		return patientProgram;
	}

	private MethodInvocation invocation(final Method method, final Object[] arguments, final Object result) {
		return new TestInvocation(
		                          method, arguments) {

			@Override
			public Object proceed() {
				return result;
			}
		};
	}

	private MethodInvocation failingInvocation(final Method method, final Object[] arguments) {
		return new TestInvocation(
		                          method, arguments) {

			@Override
			public Object proceed() {
				throw new IllegalStateException("write failed");
			}
		};
	}

	private abstract static class TestInvocation implements MethodInvocation {

		private final Method method;

		private final Object[] arguments;

		TestInvocation(Method method, Object[] arguments) {
			this.method = method;
			this.arguments = arguments;
		}

		@Override
		public Method getMethod() {
			return method;
		}

		@Override
		public Object[] getArguments() {
			return arguments;
		}

		@Override
		public Object getThis() {
			return null;
		}

		@Override
		public AccessibleObject getStaticPart() {
			return method;
		}
	}
}
