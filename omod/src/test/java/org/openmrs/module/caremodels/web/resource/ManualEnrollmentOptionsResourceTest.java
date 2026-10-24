package org.openmrs.module.caremodels.web.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.CareModelEnrollmentService;
import org.openmrs.module.caremodels.dto.ManualEnrollmentOption;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.mockito.MockedStatic;

/**
 * Contract for the read-only manual-enrollment discovery resource. Enrollment itself goes through
 * POST /caremodels/membershipoperation; this resource must never persist anything.
 */
public class ManualEnrollmentOptionsResourceTest {
	
	private MockedStatic<Context> context;
	
	private CareModelEnrollmentService enrollment;
	
	private final ManualEnrollmentOptionsResource resource = new ManualEnrollmentOptionsResource();
	
	@Before
	public void setup() {
		context = mockStatic(Context.class);
		enrollment = mock(CareModelEnrollmentService.class);
		context.when(() -> Context.getService(CareModelEnrollmentService.class)).thenReturn(enrollment);
	}
	
	@After
	public void cleanup() {
		context.close();
	}
	
	@Test
	public void serviceMustComeFromTheAuthorizedEnrollmentService() {
		assertEquals(enrollment, resource.service());
	}
	
	@Test
	public void optionResourcesAreReadOnly() {
		assertThrows(UnsupportedOperationException.class, () -> resource.save(new ManualEnrollmentOption()));
		assertThrows(UnsupportedOperationException.class,
		    () -> resource.delete(new ManualEnrollmentOption(), "reason", null));
		assertThrows(UnsupportedOperationException.class, () -> resource.purge(new ManualEnrollmentOption(), null));
		assertThrows(UnsupportedOperationException.class, () -> resource.getByUniqueId("anything"));
	}
	
	@Test
	public void defaultRepresentationExposesTheDiscoveryFieldsThePatientUiRenders() {
		DelegatingResourceDescription description = resource.getRepresentationDescription(new DefaultRepresentation());
		Set<String> properties = description.getProperties().keySet();
		assertTrue(properties.contains("modelUuid"));
		assertTrue(properties.contains("modelCode"));
		assertTrue(properties.contains("displayLabel"));
		assertTrue(properties.contains("category"));
		assertTrue(properties.contains("requiresReason"));
		assertTrue(properties.contains("alreadyEnrolled"));
		assertTrue(properties.contains("openStatus"));
		assertTrue(properties.contains("eligible"));
		assertTrue(properties.contains("reason"));
	}
	
	@Test
	public void enrollmentCheckMapsPolicyResultsToStructuredStatuses() {
		EnrollmentCheckResource check = new EnrollmentCheckResource();
		EnrollmentCheckRequest request = new EnrollmentCheckRequest();
		request.setPatient("patient");
		request.setModel("hiv");
		
		when(enrollment.evaluateManualEnrollment("patient", "hiv")).thenReturn(option(true, null, true));
		EnrollmentCheckRequest eligible = check.save(request);
		assertEquals("ELIGIBLE", eligible.getStatus());
		assertTrue(eligible.isCanEnroll());
		assertTrue(eligible.isRequiresReason());
		
		when(enrollment.evaluateManualEnrollment("patient", "hiv")).thenReturn(option(true, "PENDING", true));
		assertEquals("REQUIRES_CONFIRMATION", check.save(request).getStatus());
		
		when(enrollment.evaluateManualEnrollment("patient", "hiv")).thenReturn(option(false, "ACTIVE", false));
		EnrollmentCheckRequest enrolled = check.save(request);
		assertEquals("ALREADY_ENROLLED", enrolled.getStatus());
		assertFalse(enrolled.isCanEnroll());
		
		when(enrollment.evaluateManualEnrollment("patient", "hiv")).thenReturn(option(false, null, false));
		EnrollmentCheckRequest ineligible = check.save(request);
		assertEquals("NOT_ELIGIBLE", ineligible.getStatus());
		assertFalse(ineligible.isCanEnroll());
	}
	
	private ManualEnrollmentOption option(boolean eligible, String openStatus, boolean requiresReason) {
		ManualEnrollmentOption option = new ManualEnrollmentOption();
		option.setModelUuid("model-uuid");
		option.setModelCode("hiv");
		option.setDisplayLabel("HIV Care");
		option.setRequiresReason(requiresReason);
		option.setAlreadyEnrolled(openStatus != null);
		option.setOpenStatus(openStatus);
		option.setEligible(eligible);
		return option;
	}
	
	@Test
	public void enrollmentCheckDeclaresItsRequestShapeSoPostReachesSave() {
		DelegatingResourceDescription creatable = new EnrollmentCheckResource().getCreatableProperties();
		assertTrue(creatable.getProperties().containsKey("patient"));
		assertTrue(creatable.getProperties().containsKey("model"));
	}
	
	@Test
	public void eligibilityFlagIsRepresentedEvenWhenFalse() {
		ManualEnrollmentOption option = new ManualEnrollmentOption();
		option.setModelCode("hiv");
		option.setEligible(false);
		assertFalse(option.isEligible());
		assertEquals("hiv", option.getModelCode());
	}
}
