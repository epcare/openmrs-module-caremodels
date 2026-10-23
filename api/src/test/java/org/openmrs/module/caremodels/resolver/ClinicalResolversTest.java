package org.openmrs.module.caremodels.resolver;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.*;
import org.mockito.MockedStatic;
import org.openmrs.*;
import org.openmrs.api.*;
import org.openmrs.api.context.Context;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class ClinicalResolversTest {
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private final Instant now = Instant.parse("2026-10-21T12:00:00Z");
	
	private MockedStatic<Context> context;
	
	private Patient patient;
	
	private Concept concept;
	
	private ObsService obsService;
	
	private EncounterService encounters;
	
	private ProgramWorkflowService programs;
	
	private OrderService orders;
	
	private ConditionService conditions;
	
	private DiagnosisService diagnoses;
	
	@Before public void setup() {
        context = mockStatic(Context.class);
        patient = new Patient(7); patient.setUuid("patient-7");
        concept = new Concept(8); concept.setUuid("concept-8");
        PatientService patients = mock(PatientService.class);
        when(patients.getPatient(7)).thenReturn(patient);
        context.when(Context::getPatientService).thenReturn(patients);
        ConceptService concepts = mock(ConceptService.class);
        when(concepts.getConceptByUuid("concept-8")).thenReturn(concept);
        context.when(Context::getConceptService).thenReturn(concepts);
        obsService = mock(ObsService.class); context.when(Context::getObsService).thenReturn(obsService);
        encounters = mock(EncounterService.class); context.when(Context::getEncounterService).thenReturn(encounters);
        programs = mock(ProgramWorkflowService.class); context.when(Context::getProgramWorkflowService).thenReturn(programs);
        orders = mock(OrderService.class); context.when(Context::getOrderService).thenReturn(orders);
        conditions = mock(ConditionService.class); context.when(Context::getConditionService).thenReturn(conditions);
        diagnoses = mock(DiagnosisService.class); context.when(Context::getDiagnosisService).thenReturn(diagnoses);
    }
	
	@After
	public void cleanup() {
		context.close();
	}
	
	private JsonNode checked(JsonNode result) {
		org.openmrs.module.caremodels.config.ValidationResult validation = new org.openmrs.module.caremodels.config.JsonSchemaValidator()
		        .validate(result, org.openmrs.module.caremodels.config.JsonSchemaValidator.SchemaType.CLINICAL_RESULT);
		assertTrue(validation.toString(), validation.isValid());
		return result;
	}
	
	private JsonNode source(String json) throws Exception {
		return mapper.readTree(json);
	}
	
	private Obs observation(String uuid, String date, Double numeric, String text, String modifier) {
		Obs obs = new Obs();
		obs.setUuid(uuid);
		obs.setPerson(patient);
		obs.setConcept(concept);
		obs.setObsDatetime(Date.from(Instant.parse(date)));
		obs.setValueNumeric(numeric);
		obs.setValueText(text);
		obs.setValueModifier(modifier);
		return obs;
	}
	
	private void observations(List<Obs> values) {
		when(
		    obsService.getObservations(anyList(), isNull(), anyList(), isNull(), isNull(), isNull(), anyList(), anyInt(),
		        isNull(), any(), any(), eq(false))).thenReturn(values);
	}
	
	@Test
	public void observationContractShouldPreserveQualifiersDatesAndDeterministicTies() throws Exception {
		Obs numeric = observation("b", "2026-10-20T10:00:00Z", 12.0, null, null);
		Obs qualified = observation("a", "2026-10-20T10:00:00Z", 50.0, null, "<");
		Obs voided = observation("c", "2026-10-20T10:00:00Z", 99.0, null, null);
		voided.setVoided(true);
		observations(Arrays.asList(numeric, qualified, voided));
		JsonNode result = new LatestObservationResolver("historical-observations", Clock.fixed(now, ZoneOffset.UTC))
		        .resolve(7, source("{\"maxResults\":2,\"windowDays\":30}"), "concept-8");
		checked(result);
		assertEquals("a", result.path("values").get(0).path("obsUuid").asText());
		assertEquals("STRING", result.path("values").get(0).path("valueType").asText());
		assertEquals("<50.0", result.path("values").get(0).path("value").asText());
		assertEquals("2026-10-20T10:00:00Z", result.path("values").get(0).path("effectiveTime").asText());
		assertEquals(2, result.path("values").size());
		verify(obsService).getObservations(anyList(), isNull(), anyList(), isNull(), isNull(), isNull(),
		    eq(Arrays.asList("obsDatetime desc", "uuid asc")), eq(3), isNull(),
		    eq(Date.from(now.minusSeconds(30 * 86400L))), eq(Date.from(now)), eq(false));
	}
	
	@Test
	public void observationsShouldExcludeForeignPatientFutureAndOldValuesAndReportTruncation() throws Exception {
		Obs foreign = observation("foreign", "2026-10-20T10:00:00Z", 1.0, null, null);
		foreign.setPerson(new Patient(99));
		observations(Arrays.asList(foreign, observation("future", "2027-10-20T10:00:00Z", 1.0, null, null),
		    observation("old", "2020-10-20T10:00:00Z", 1.0, null, null)));
		DataResolver resolver = new LatestObservationResolver("latest-observation", Clock.fixed(now, ZoneOffset.UTC));
		assertEquals("no-records", resolver.resolve(7, source("{\"windowDays\":30}"), "concept-8").path("status").asText());
		observations(Arrays.asList(observation("b", "2026-10-20T10:00:00Z", 1.0, null, null),
		    observation("a", "2026-10-20T10:00:00Z", 2.0, null, null)));
		JsonNode result = resolver.resolve(7, source("{}"), "concept-8");
		assertTrue(result.path("truncated").asBoolean());
		assertTrue(result.path("total").isNull());
	}
	
	@Test
	public void shouldKeepCodedTextAndDateResultsCompatible() throws Exception {
		Obs coded = observation("coded", "2026-10-20T10:00:00Z", null, null, null);
		coded.setValueCoded(concept);
		observations(Collections.singletonList(coded));
		DataResolver resolver = new LatestObservationResolver("latest-observation", Clock.fixed(now, ZoneOffset.UTC));
		assertEquals("CODED", resolver.resolve(7, source("{}"), "concept-8").path("values").get(0).path("valueType")
		        .asText());
		Obs date = observation("date", "2026-10-20T10:00:00Z", null, null, null);
		date.setValueDatetime(Date.from(now));
		observations(Collections.singletonList(date));
		assertEquals(now.toString(), resolver.resolve(7, source("{}"), "concept-8").path("values").get(0).path("value")
		        .asText());
	}
	
	@Test
	public void actualEncountersShouldBeBoundedAndTyped() throws Exception {
		EncounterType type = new EncounterType(4);
		type.setUuid("enc-type");
		type.setName("Review");
		when(encounters.getEncounterTypeByUuid("enc-type")).thenReturn(type);
		Encounter encounter = new Encounter();
		encounter.setUuid("enc-1");
		encounter.setPatient(patient);
		encounter.setEncounterType(type);
		encounter.setEncounterDatetime(Date.from(now));
		Encounter voided = new Encounter();
		voided.setVoided(true);
		when(
		    encounters.getEncounters(eq(patient), isNull(), any(), any(), isNull(), anyCollection(), isNull(), isNull(),
		        isNull(), eq(false))).thenReturn(Arrays.asList(encounter, voided));
		JsonNode result = new EncounterResolver().resolve(7, source("{\"maxResults\":1}"), "enc-type");
		checked(result);
		assertEquals(1, result.path("records").size());
		assertFalse(result.has("values"));
		assertEquals("enc-1", result.path("records").get(0).path("uuid").asText());
		assertEquals("recorded", result.path("records").get(0).path("status").asText());
		verifyNoInteractions(obsService);
	}
	
	@Test
	public void programEpisodesShouldNotCollapseToLatestEnrollment() throws Exception {
		Program program = new Program(3);
		program.setUuid("program");
		when(programs.getProgramByUuid("program")).thenReturn(program);
		PatientProgram first = new PatientProgram();
		first.setUuid("ep-1");
		first.setPatient(patient);
		first.setProgram(program);
		first.setDateEnrolled(Date.from(now.minusSeconds(10000)));
		first.setDateCompleted(Date.from(now.minusSeconds(5000)));
		PatientProgram second = new PatientProgram();
		second.setUuid("ep-2");
		second.setPatient(patient);
		second.setProgram(program);
		second.setDateEnrolled(Date.from(now.minusSeconds(1000)));

        ProgramWorkflow workflow = new ProgramWorkflow(); workflow.setUuid("workflow");
        ProgramWorkflowState configuredState = new ProgramWorkflowState(); configuredState.setUuid("configured-state"); configuredState.setProgramWorkflow(workflow);
        PatientState currentState = new PatientState(); currentState.setUuid("current-state"); currentState.setState(configuredState); currentState.setStartDate(Date.from(now.minusSeconds(500)));
        PatientState voidedState = new PatientState(); voidedState.setUuid("voided-state"); voidedState.setState(configuredState); voidedState.setStartDate(Date.from(now.minusSeconds(500))); voidedState.setVoided(true);
        second.setStates(new LinkedHashSet<>(Arrays.asList(currentState, voidedState)));
		when(programs.getPatientPrograms(eq(patient), eq(program), any(), any(), isNull(), isNull(), eq(false))).thenReturn(
		    Arrays.asList(first, second));
		JsonNode records = checked(new ProgramStateResolver().resolve(7, source("{}"), "program")).path("records");
		assertEquals(2, records.size());
		assertEquals("ep-2", records.get(0).path("uuid").asText());
		assertEquals(0, records.get(1).path("currentStates").size());
		assertEquals(1, records.get(0).path("currentStates").size());
		assertEquals("configured-state", records.get(0).path("currentStates").get(0).path("stateUuid").asText());
	}
	
	@Test
	public void patientFieldsMustBeExplicitAndMinimal() throws Exception {
		patient.setGender("F");
		JsonNode result = new PatientSummaryResolver().resolve(7, source("{\"fields\":[\"gender\"]}"), null);
		checked(result);
		assertEquals("F", result.path("fields").path("gender").asText());
		assertEquals(1, result.path("fields").size());
		try {
			new PatientSummaryResolver().resolve(7, source("{\"fields\":[\"address\"]}"), null);
			fail();
		}
		catch (IllegalArgumentException expected) {}
		verifyNoInteractions(obsService, encounters, programs);
	}
	
	@Test
	public void orderHistoryShouldPreserveReplacementAndStoppedDates() throws Exception {
		DrugOrder first = mock(DrugOrder.class);
		DrugOrder second = mock(DrugOrder.class);
		for (DrugOrder order : Arrays.asList(first, second)) {
			when(order.getPatient()).thenReturn(patient);
			when(order.getConcept()).thenReturn(concept);
			when(order.getVoided()).thenReturn(false);
			when(order.getDateActivated()).thenReturn(Date.from(now.minusSeconds(1000)));
		}
		when(first.getUuid()).thenReturn("first");
		when(first.getAction()).thenReturn(Order.Action.NEW);
		when(first.getEffectiveStopDate()).thenReturn(Date.from(now.minusSeconds(500)));
		when(second.getUuid()).thenReturn("second");
		when(second.getAction()).thenReturn(Order.Action.REVISE);
		when(second.getPreviousOrder()).thenReturn(first);
		when(second.isActive(any(Date.class))).thenReturn(true);
		when(orders.getOrders(any(org.openmrs.parameter.OrderSearchCriteria.class)))
		        .thenReturn(Arrays.asList(second, first));
		JsonNode rows = checked(new OrderResolver().resolve(7, source("{}"), "concept-8")).path("records");
		assertFalse(rows.get(0).path("activeAtAssessment").asBoolean());
		assertFalse(rows.get(0).path("effectiveStopDate").isNull());
		assertEquals("first", rows.get(1).path("previousOrderUuid").asText());
		assertTrue(rows.get(1).path("activeAtAssessment").asBoolean());
	}
	
	@Test
	public void conditionsAndDiagnosesMustRemainDistinct() throws Exception {
		Condition condition = new Condition();
		condition.setUuid("condition");
		condition.setPatient(patient);
		condition.setOnsetDate(Date.from(now.minusSeconds(1000)));
		condition.setClinicalStatus(ConditionClinicalStatus.INACTIVE);
		condition.setEndDate(Date.from(now));
		CodedOrFreeText name = new CodedOrFreeText();
		name.setNonCoded("Recorded condition");
		condition.setCondition(name);
		Condition old = new Condition();
		old.setUuid("old");
		old.setPatient(patient);
		old.setOnsetDate(Date.from(now.minusSeconds(2000)));
		condition.setPreviousVersion(old);
		Condition voided = new Condition();
		voided.setVoided(true);
		when(conditions.getAllConditions(patient)).thenReturn(Arrays.asList(old, condition, voided));
		JsonNode result = new ConditionResolver().resolve(7, source("{}"), null);
		checked(result);
		assertEquals("longitudinal-conditions", result.path("scope").asText());
		assertEquals("INACTIVE", result.path("records").get(0).path("clinicalStatus").asText());
		assertEquals("Recorded condition", result.path("records").get(0).path("name").asText());
		assertEquals(1, result.path("records").size());
		when(diagnoses.getDiagnoses(eq(patient), any())).thenReturn(Collections.emptyList());
		assertEquals("no-records", checked(new DiagnosisResolver().resolve(7, source("{}"), null)).path("status").asText());
	}
	
	@Test
	public void boundsAndUnavailableReferencesMustFailClosed() throws Exception {
		for (String declaration : Arrays.asList("{\"maxResults\":0}", "{\"maxResults\":1001}", "{\"includeVoided\":true}",
		    "{\"windowDays\":3651}")) {
			try {
				new LatestObservationResolver().resolve(7, source(declaration), "concept-8");
				fail(declaration);
			}
			catch (IllegalArgumentException expected) {}
		}
		concept.setRetired(true);
		try {
			new LatestObservationResolver().resolve(7, source("{}"), "concept-8");
			fail();
		}
		catch (IllegalArgumentException expected) {}
		verifyNoInteractions(obsService);
	}
}
