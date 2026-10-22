package org.openmrs.module.caremodels.event;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.*;
import org.mockito.MockedStatic;
import org.openmrs.*;
import org.openmrs.api.*;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.rule.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class EnrollmentEvidenceCompatibilityTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private final Date asOf = Date.from(java.time.Instant.parse("2025-01-10T00:00:00Z"));

	private MockedStatic<Context> context;

	private OpenmrsEnrollmentRuleEvidenceFactory factory;

	private Patient patient;

	private Concept concept;

	private ObsService observations;

	private ConceptService concepts;

	private ProgramWorkflowService programs;

	private JsonNode definition;

	@Before public void setup() throws Exception {
        context = mockStatic(Context.class); factory = new OpenmrsEnrollmentRuleEvidenceFactory();
        patient = new Patient(7); patient.setUuid("patient"); concept = new Concept(8); concept.setUuid("concept");
        PatientService patients = mock(PatientService.class); when(patients.getPatient(7)).thenReturn(patient);
        context.when(Context::getPatientService).thenReturn(patients);
        concepts = mock(ConceptService.class); when(concepts.getConceptByUuid("concept")).thenReturn(concept);
        context.when(Context::getConceptService).thenReturn(concepts);
        observations = mock(ObsService.class); context.when(Context::getObsService).thenReturn(observations);
        programs = mock(ProgramWorkflowService.class); context.when(Context::getProgramWorkflowService).thenReturn(programs);
        definition = mapper.readTree("{\"terminology\":{\"concepts\":{\"measurement\":\"concept\"},\"programs\":{\"care\":\"program\"},\"codedAnswers\":{\"earlier\":\"earlier-concept\",\"later\":\"later-concept\"}}}");
    }

	@After
	public void cleanup() {
		if (context != null)
			context.close();
	}

	private Obs obs(String modifier) {
		Obs obs = new Obs();
		obs.setUuid("observation");
		obs.setPerson(patient);
		obs.setConcept(concept);
		obs.setValueNumeric(50.0);
		obs.setValueModifier(modifier);
		obs.setObsDatetime(asOf);
		return obs;
	}

	private void returned(Obs obs) {
		when(
		    observations.getObservations(anyList(), isNull(), anyList(), isNull(), isNull(), isNull(), anyList(), eq(1),
		        isNull(), isNull(), eq(asOf), eq(false))).thenReturn(Collections.singletonList(obs));
	}

	@Test
	public void qualifiedObservationsMustNotBeTurnedIntoExactEnrollmentMeasurements() throws Exception {
		returned(obs("<"));
		RuleEvidenceProvider evidence = factory.create(7, definition, asOf);
		assertEquals("<50.0", evidence.resolve("observation", "measurement").getValue());
		JsonNode predicate = mapper
		        .readTree("{\"type\":\"observation\",\"alias\":\"measurement\",\"operator\":\"LESS_THAN\",\"value\":100}");
		assertEquals(TriState.UNKNOWN, new RuleExpressionEvaluator().evaluate(predicate, evidence, asOf));
	}

	@Test
	public void exactMeasurementsMustUseBoundedDeterministicObservationOrdering() {
		returned(obs(null));
		assertEquals(50.0, factory.create(7, definition, asOf).resolve("observation", "measurement").getValue());
		verify(observations).getObservations(anyList(), isNull(), anyList(), isNull(), isNull(), isNull(),
		    eq(Arrays.asList("obsDatetime desc", "uuid asc")), eq(1), isNull(), isNull(), eq(asOf), eq(false));
	}

	@Test
	public void retiredOrVoidedEvidenceMustNotEnroll() {
		concept.setRetired(true);
		assertEquals(RuleEvidence.Availability.UNKNOWN,
		    factory.create(7, definition, asOf).resolve("observation", "measurement").getAvailability());
		verifyNoInteractions(observations);
		concept.setRetired(false);
		Obs voided = obs(null);
		voided.setVoided(true);
		returned(voided);
		assertEquals(RuleEvidence.Availability.UNKNOWN,
		    factory.create(7, definition, asOf).resolve("observation", "measurement").getAvailability());
	}

	@Test public void programReplayMustUseStatesAtEvaluationTimeInsteadOfTodaysCurrentStates() {
        Program program = new Program(3); program.setUuid("program"); when(programs.getProgramByUuid("program")).thenReturn(program);
        PatientProgram episode = new PatientProgram(); episode.setPatient(patient); episode.setProgram(program); episode.setUuid("episode"); episode.setDateEnrolled(new Date(asOf.getTime()-100000));
        episode.setDateCompleted(new Date(asOf.getTime()+100000));
        ProgramWorkflowState earlierState = new ProgramWorkflowState(); Concept earlier = new Concept(10); earlier.setUuid("earlier-concept"); earlierState.setConcept(earlier);
        ProgramWorkflowState laterState = new ProgramWorkflowState(); Concept later = new Concept(11); later.setUuid("later-concept"); laterState.setConcept(later);
        PatientState earlierRecorded = new PatientState(); earlierRecorded.setUuid("first"); earlierRecorded.setState(earlierState); earlierRecorded.setStartDate(new Date(asOf.getTime()-10000)); earlierRecorded.setEndDate(new Date(asOf.getTime()+10000));
        PatientState laterRecorded = new PatientState(); laterRecorded.setUuid("second"); laterRecorded.setState(laterState); laterRecorded.setStartDate(new Date(asOf.getTime()+10000));
        episode.setStates(new LinkedHashSet<>(Arrays.asList(earlierRecorded, laterRecorded)));
        when(programs.getPatientPrograms(eq(patient), eq(program), isNull(), eq(asOf), isNull(), isNull(), eq(false))).thenReturn(Collections.singletonList(episode));
        assertEquals("earlier", factory.create(7, definition, asOf).resolve("program", "care").getValue());
        episode.setStates(Collections.emptySet());
        assertEquals("ACTIVE", factory.create(7, definition, asOf).resolve("program", "care").getValue());
    }
}
