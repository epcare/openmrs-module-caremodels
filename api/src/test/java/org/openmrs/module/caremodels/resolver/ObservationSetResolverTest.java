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
import org.openmrs.module.caremodels.config.ValidationResult;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import static org.junit.Assert.*;

/** Grouped observation-set resolver: parent groups, typed members, bounds and voided handling. */
public class ObservationSetResolverTest {
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private final Instant now = Instant.parse("2026-10-24T12:00:00Z");
	
	private MockedStatic<Context> context;
	
	private Patient patient;
	
	private Concept set;
	
	private ConceptNumeric creatinine;
	
	private Concept stage;
	
	private Concept notes;
	
	private Concept visitDate;
	
	private ObsService obsService;
	
	private ConceptService conceptService;
	
	private final ObservationSetResolver resolver = new ObservationSetResolver(Clock.fixed(now, ZoneOffset.UTC));
	
	/** Context's class init loads log4j config from the application data directory. */
	@BeforeClass
	public static void applicationDataDirectory() {
		System.setProperty("OPENMRS_APPLICATION_DATA_DIRECTORY", System.getProperty("java.io.tmpdir"));
	}
	
	@Before public void setup() {
		context = mockStatic(Context.class);
		patient = new Patient(7); patient.setUuid("patient-7");

		set = concept(10, "renal-set", "Renal Function Panel", "N/A");
		set.setSet(true);
		creatinine = new ConceptNumeric(11); creatinine.setUuid("concept-creatinine");
		creatinine.setDatatype(datatype("Numeric"));
		creatinine.setPreferredName(new ConceptName("Creatinine", Locale.ENGLISH));
		creatinine.setUnits("mg/dL"); creatinine.setLowNormal(0.6); creatinine.setHiNormal(1.3);
		stage = concept(12, "concept-stage", "WHO Stage", "Coded");
		notes = concept(13, "concept-notes", "Clinical Notes", "Text");
		visitDate = concept(14, "concept-visitdate", "Visit Date", "Datetime");
		for (Concept member : Arrays.asList(creatinine, stage, notes, visitDate))
			set.addSetMember(member);

		conceptService = mock(ConceptService.class);
		when(conceptService.getConceptByUuid("renal-set")).thenReturn(set);
		context.when(Context::getConceptService).thenReturn(conceptService);
		PatientService patients = mock(PatientService.class);
		when(patients.getPatient(7)).thenReturn(patient);
		context.when(Context::getPatientService).thenReturn(patients);
		// OpenMRS stores boolean observations as coded TRUE/FALSE concepts
		trueConcept = concept(20, "concept-true", "TRUE", "Coded");
		falseConcept = concept(21, "concept-false", "FALSE", "Coded");
		when(conceptService.getTrueConcept()).thenReturn(trueConcept);
		obsService = mock(ObsService.class);
		context.when(Context::getObsService).thenReturn(obsService);
	}
	
	@After
	public void cleanup() {
		context.close();
	}
	
	private Concept concept(int id, String uuid, String name, String datatypeName) {
		Concept concept = new Concept(id);
		concept.setUuid(uuid);
		concept.setDatatype(datatype(datatypeName));
		ConceptName conceptName = new ConceptName(name, Locale.ENGLISH);
		concept.setPreferredName(conceptName);
		return concept;
	}
	
	private ConceptDatatype datatype(String name) {
		ConceptDatatype datatype = new ConceptDatatype();
		datatype.setName(name);
		// OpenMRS keys datatype helpers off well-known UUIDs, not the name
		if ("Numeric".equals(name))
			datatype.setUuid(ConceptDatatype.NUMERIC_UUID);
		if ("Coded".equals(name))
			datatype.setUuid(ConceptDatatype.CODED_UUID);
		if ("Text".equals(name))
			datatype.setUuid(ConceptDatatype.TEXT_UUID);
		if ("Datetime".equals(name))
			datatype.setUuid(ConceptDatatype.DATETIME_UUID);
		if ("Date".equals(name))
			datatype.setUuid(ConceptDatatype.DATE_UUID);
		if ("Boolean".equals(name))
			datatype.setUuid(ConceptDatatype.BOOLEAN_UUID);
		return datatype;
	}
	
	private Concept trueConcept;
	
	private Concept falseConcept;
	
	private Concept codedAnswer(String uuid, String display) {
		Concept answer = new Concept(99);
		answer.setUuid(uuid);
		answer.setDatatype(datatype("Coded"));
		answer.setPreferredName(new ConceptName(display, Locale.ENGLISH));
		return answer;
	}
	
	private Obs parent(String uuid, String date, Encounter encounter, Obs... children) {
		Obs obs = new Obs();
		obs.setUuid(uuid);
		obs.setPerson(patient);
		obs.setConcept(set);
		obs.setObsDatetime(Date.from(Instant.parse(date)));
		if (encounter != null) obs.setEncounter(encounter);
		obs.setGroupMembers(new HashSet<>(Arrays.asList(children)));
		return obs;
	}
	
	private Obs child(String uuid, Concept concept, String date) {
		Obs obs = new Obs();
		obs.setUuid(uuid);
		obs.setPerson(patient);
		obs.setConcept(concept);
		obs.setObsDatetime(Date.from(Instant.parse(date)));
		return obs;
	}
	
	private JsonNode source(String json) throws Exception {
		return mapper.readTree(json);
	}
	
	private JsonNode resolve(String sourceJson, List<Obs> parents) throws Exception {
		when(
		    obsService.getObservations(anyList(), isNull(), anyList(), isNull(), isNull(), isNull(), anyList(), any(),
		        isNull(), any(), any(), anyBoolean())).thenReturn(parents);
		ValidationResult sourceValidation = SourceContract.validate(source(sourceJson), new DataResolverRegistry(),
		    "$.source");
		assertTrue(sourceValidation.toString(), sourceValidation.isValid());
		return resolver.resolve(7, source(sourceJson), "renal-set");
	}
	
	private String setSource() {
		return "{\"type\":\"observation-set\",\"conceptAlias\":\"renalPanel\",\"resultType\":\"TABLE\"}";
	}
	
	private ValidationResult validateResult(JsonNode result) {
		return new org.openmrs.module.caremodels.config.JsonSchemaValidator().validate(result,
		    org.openmrs.module.caremodels.config.JsonSchemaValidator.SchemaType.CLINICAL_RESULT);
	}
	
	@Test
	public void latestIsTheNewestParentGroupNotASyntheticCombination() throws Exception {
		Obs older = parent("parent-old", "2026-01-10T09:00:00Z", null, numeric("obs-old-crea", "2026-01-10T09:05:00Z", 1.1));
		Obs newer = parent("parent-new", "2026-09-01T09:00:00Z", null, numeric("obs-new-crea", "2026-09-01T09:05:00Z", 1.2));
		JsonNode result = resolve(setSource(), Arrays.asList(newer, older));
		assertEquals(2, result.path("groups").size());
		assertEquals("parent-new", result.path("groups").get(0).path("parentObsUuid").asText());
		assertEquals(1, result.path("groups").get(0).path("members").size());
	}
	
	@Test
	public void mixedDatatypesAreTypedOnTheWire() throws Exception {
		Obs stageChild = child("obs-stage", stage, "2026-09-01T09:06:00Z");
		stageChild.setValueCoded(codedAnswer("stage-ii", "Stage II"));
		Obs notesChild = child("obs-notes", notes, "2026-09-01T09:07:00Z");
		notesChild.setValueText("Clinically stable");
		Obs dateChild = child("obs-date", visitDate, "2026-09-01T09:08:00Z");
		dateChild.setValueDatetime(Date.from(Instant.parse("2026-09-01T00:00:00Z")));
		JsonNode result = resolve(
		    setSource(),
		    Collections.singletonList(parent("parent-1", "2026-09-01T09:00:00Z", null,
		        numeric("obs-crea", "2026-09-01T09:05:00Z", 1.2), stageChild, notesChild, dateChild)));
		JsonNode members = result.path("groups").get(0).path("members");
		assertEquals("NUMERIC", members.get(0).path("datatype").asText());
		assertEquals(1.2, members.get(0).path("value").asDouble(), 0.0001);
		assertEquals("mg/dL", members.get(0).path("unit").asText());
		assertEquals("CODED", members.get(1).path("datatype").asText());
		assertEquals("stage-ii", members.get(1).path("value").path("conceptUuid").asText());
		assertEquals("Stage II", members.get(1).path("value").path("display").asText());
		assertEquals("TEXT", members.get(2).path("datatype").asText());
		assertEquals("Clinically stable", members.get(2).path("value").asText());
		assertEquals("DATETIME", members.get(3).path("datatype").asText());
		assertTrue(validateResult(result).isValid());
	}
	
	@Test
	public void referenceRangeComesFromTheConceptDefinitionOnly() throws Exception {
		JsonNode result = resolve(
		    setSource(),
		    Collections.singletonList(parent("parent-1", "2026-09-01T09:00:00Z", null,
		        numeric("obs-crea", "2026-09-01T09:05:00Z", 5.0))));
		JsonNode range = result.path("groups").get(0).path("members").get(0).path("referenceRange");
		assertEquals(0.6, range.path("low").asDouble(), 0.0001);
		assertEquals(1.3, range.path("high").asDouble(), 0.0001);
		assertTrue(validateResult(result).isValid());
	}
	
	@Test
	public void qualifiersArePreservedVerbatim() throws Exception {
		Obs qualified = numeric("obs-qual", "2026-09-01T09:05:00Z", 0.2);
		qualified.setValueModifier("<");
		JsonNode result = resolve(setSource(),
		    Collections.singletonList(parent("parent-1", "2026-09-01T09:00:00Z", null, qualified)));
		assertEquals("<", result.path("groups").get(0).path("members").get(0).path("qualifier").asText());
		assertEquals(0.2, result.path("groups").get(0).path("members").get(0).path("value").asDouble(), 0.0001);
	}
	
	@Test
	public void booleanAndUnknownDatatypesStayTypedOrNull() throws Exception {
		Concept yesNo = concept(15, "concept-yn", "Smoker", "Boolean");
		Obs booleanChild = child("obs-bool", yesNo, "2026-09-01T09:06:00Z");
		// OpenMRS stores boolean observations as the coded TRUE concept
		booleanChild.setValueCoded(trueConcept);
		Concept weird = concept(16, "concept-weird", "Complex", "N/A");
		Obs unknownChild = child("obs-unknown", weird, "2026-09-01T09:07:00Z");
		unknownChild.setValueText("n/a");
		JsonNode result = resolve(setSource(),
		    Collections.singletonList(parent("parent-1", "2026-09-01T09:00:00Z", null, booleanChild, unknownChild)));
		JsonNode members = result.path("groups").get(0).path("members");
		assertEquals("UNKNOWN", members.get(0).path("datatype").asText());
		assertTrue(members.get(0).path("value").isNull());
		assertEquals("BOOLEAN", members.get(1).path("datatype").asText());
		assertTrue(members.get(1).path("value").asBoolean());
	}
	
	@Test
	public void missingMembersStayMissing() throws Exception {
		// the recorded group only contains creatinine; the other set members are absent
		JsonNode result = resolve(
		    setSource(),
		    Collections.singletonList(parent("parent-1", "2026-09-01T09:00:00Z", null,
		        numeric("obs-crea", "2026-09-01T09:05:00Z", 1.2))));
		assertEquals(1, result.path("groups").get(0).path("members").size());
		assertEquals("concept-creatinine", result.path("groups").get(0).path("members").get(0).path("conceptUuid").asText());
	}
	
	@Test
	public void repeatedMeasurementsOfOneMemberAreAllKept() throws Exception {
		Obs first = numeric("obs-crea-1", "2026-09-01T09:05:00Z", 1.2);
		Obs second = numeric("obs-crea-2", "2026-09-01T11:30:00Z", 1.4);
		JsonNode result = resolve(setSource(),
		    Collections.singletonList(parent("parent-1", "2026-09-01T09:00:00Z", null, first, second)));
		JsonNode members = result.path("groups").get(0).path("members");
		assertEquals(2, members.size());
		// same member, same name: newest recorded value first, both preserved
		assertEquals("obs-crea-2", members.get(0).path("obsUuid").asText());
		assertEquals("2026-09-01T11:30:00Z", members.get(0).path("effectiveTime").asText());
		assertEquals("obs-crea-1", members.get(1).path("obsUuid").asText());
	}
	
	@Test public void voidedParentsAndChildrenAreExcluded() throws Exception {
		Obs voidedParent = parent("parent-voided", "2026-09-05T09:00:00Z", null);
		voidedParent.setVoided(true); voidedParent.setDateVoided(new Date()); voidedParent.setVoidedBy(new User(1));
		Obs parent = parent("parent-1", "2026-09-01T09:00:00Z", null, numeric("obs-void-child", "2026-09-01T09:05:00Z", 9.9));
		parent.getGroupMembers().forEach(child -> child.setVoided(true));
		JsonNode result = resolve(setSource(), Arrays.asList(voidedParent, parent));
		assertEquals(1, result.path("groups").size());
		// the surviving group exists but has zero visible members
		assertEquals(0, result.path("groups").get(0).path("members").size());
	}
	
	@Test
	public void sameDateParentGroupsBreakTiesByUuid() throws Exception {
		Obs a = parent("parent-a", "2026-09-01T09:00:00Z", null);
		Obs b = parent("parent-b", "2026-09-01T09:00:00Z", null);
		JsonNode result = resolve(setSource(), Arrays.asList(a, b));
		assertEquals(2, result.path("groups").size());
		assertEquals("parent-a", result.path("groups").get(0).path("parentObsUuid").asText());
		assertEquals("parent-b", result.path("groups").get(1).path("parentObsUuid").asText());
	}
	
	@Test
	public void encounterUuidIsPreservedWhenAvailable() throws Exception {
		Encounter encounter = new Encounter(3);
		encounter.setUuid("encounter-3");
		JsonNode result = resolve(
		    setSource(),
		    Collections.singletonList(parent("parent-1", "2026-09-01T09:00:00Z", encounter,
		        numeric("obs-crea", "2026-09-01T09:05:00Z", 1.2))));
		assertEquals("encounter-3", result.path("groups").get(0).path("encounterUuid").asText());
	}
	
	@Test
	public void windowAndLimitsAreBounded() throws Exception {
		List<Obs> many = new ArrayList<Obs>();
		for (int i = 0; i < 4; i++)
			many.add(parent("parent-" + i, String.format("2026-09-0%dT09:00:00Z", i + 1), null));
		JsonNode result = resolve("{\"type\":\"observation-set\",\"conceptAlias\":\"renalPanel\",\"resultType\":\"TABLE\","
		        + "\"windowDays\":365,\"maxResults\":2}", many);
		assertEquals(2, result.path("groups").size());
		assertTrue(result.path("truncated").asBoolean());
		assertTrue(validateResult(result).isValid());
	}
	
	@Test public void nestedGroupsArePreservedUpToTheDepthCap() throws Exception {
		Concept nested = concept(17, "concept-nested", "Urine Panel", "N/A");
		set.addSetMember(nested);
		Obs nestedParent = child("obs-nested", nested, "2026-09-01T09:04:00Z");
		nestedParent.setGroupMembers(new HashSet<>(Collections.singletonList(
		    numeric("obs-nested-child", "2026-09-01T09:04:30Z", 6.5))));
		JsonNode result = resolve(setSource(), Collections.singletonList(
		    parent("parent-1", "2026-09-01T09:00:00Z", null, numeric("obs-crea", "2026-09-01T09:05:00Z", 1.2),
		        nestedParent)));
		// set display order first: creatinine (declared member), then the nested group
		JsonNode creatinineMember = result.path("groups").get(0).path("members").get(0);
		assertEquals("concept-creatinine", creatinineMember.path("conceptUuid").asText());
		JsonNode nestedMembers = result.path("groups").get(0).path("members").get(1).path("members");
		assertEquals(1, nestedMembers.size());
		assertEquals(6.5, nestedMembers.get(0).path("value").asDouble(), 0.0001);
		assertTrue(validateResult(result).isValid());
	}
	
	@Test
	public void nonSetParentConceptFailsClosed() throws Exception {
		Concept notASet = concept(18, "concept-notset", "Just A Concept", "N/A");
		when(conceptService.getConceptByUuid("not-a-set")).thenReturn(notASet);
		try {
			resolver.resolve(7, source(setSource()), "not-a-set");
			fail("An N/A concept that is not a set must be rejected");
		}
		catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("concept set"));
		}
	}
	
	@Test
	public void emptyConceptSetFailsClosed() throws Exception {
		Concept empty = concept(19, "empty-set", "Empty Panel", "N/A");
		empty.setSet(true);
		when(conceptService.getConceptByUuid("empty-set")).thenReturn(empty);
		try {
			resolver.resolve(7, source(setSource()), "empty-set");
			fail("An empty concept set must be rejected");
		}
		catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("no members"));
		}
	}
	
	@Test
	public void unresolvableParentConceptFailsClosed() throws Exception {
		try {
			resolver.resolve(7, source(setSource()), "missing-concept");
			fail("Missing concept must be rejected");
		}
		catch (IllegalArgumentException expected) {
			// expected
		}
	}
	
	@Test
	public void unknownPatientFailsClosed() throws Exception {
		try {
			resolver.resolve(null, source(setSource()), "renal-set-uuid");
			fail("Authorization must provide a patient");
		}
		catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("patient"));
		}
	}
	
	private Obs numeric(String uuid, String date, double value) {
		Obs obs = child(uuid, creatinine, date);
		obs.setValueNumeric(value);
		return obs;
	}
}
