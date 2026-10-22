/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.web.resource;

import static org.junit.Assert.assertEquals;

import java.util.Date;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openmrs.api.APIException;
import org.openmrs.module.caremodels.api.CareModelEnrollmentService;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.model.CareModelMember.MemberStatus;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Action;

public class MembershipOperationResourceTest {

	private RecordingService service;

	private MembershipOperationResource resource;

	@BeforeClass
	public static void configureApplicationDataDirectory() {
		System.setProperty("OPENMRS_APPLICATION_DATA_DIRECTORY", System.getProperty("java.io.tmpdir"));
	}

	@Before
	public void setup() {
		service = new RecordingService();
		resource = new MembershipOperationResource() {

			@Override
			protected CareModelEnrollmentService service() {
				return service;
			}
		};
	}

	@Test
	public void shouldDispatchManualEnrollmentUsingStableIdentifiers() {
		MembershipOperationRequest request = request("manual", "Clinical decision");
		request.setPatient("patient-uuid");
		request.setModel("HIV-CARE");
		MembershipOperationRequest result = resource.save(request);

		assertEquals("patient-uuid", service.patient);
		assertEquals("HIV-CARE", service.model);
		assertEquals("ACTIVE", result.getStatus());
		assertEquals("member-uuid", result.getMemberUuid());
	}

	@Test
	public void shouldDispatchConfirmationDecision() {
		MembershipOperationRequest request = request("confirm", "Patient accepted");
		request.setMemberUuid("proposal-uuid");
		resource.save(request);
		assertEquals("confirm", service.operation);
		assertEquals("proposal-uuid", service.memberUuid);
	}

	@Test
	public void shouldMapEverySupportedLifecycleTransition() {
		assertTransition("SUSPEND", MemberStatus.SUSPENDED);
		assertTransition("RESUME", MemberStatus.ACTIVE);
		assertTransition("COMPLETE", MemberStatus.COMPLETED);
		assertTransition("WITHDRAW", MemberStatus.WITHDRAWN);
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectUnknownTransition() {
		MembershipOperationRequest request = request("transition", "Reason");
		request.setMemberUuid("member-uuid");
		request.setTransition("DELETE");
		resource.save(request);
	}

	private void assertTransition(String transition, MemberStatus expected) {
		MembershipOperationRequest request = request("transition", "Clinical decision");
		request.setMemberUuid("member-uuid");
		request.setTransition(transition);
		resource.save(request);
		assertEquals(expected, service.status);
	}

	private MembershipOperationRequest request(String operation, String reason) {
		MembershipOperationRequest request = new MembershipOperationRequest();
		request.setOperation(operation);
		request.setReason(reason);
		return request;
	}

	private static final class RecordingService implements CareModelEnrollmentService {

		private String operation;

		private String patient;

		private String model;

		private String memberUuid;

		private MemberStatus status;

		@Override
		public CareModelMember enrollManually(String patientUuid, String modelCodeOrUuid, String reason, Date asOf) {
			operation = "manual";
			patient = patientUuid;
			model = modelCodeOrUuid;
			return member(MemberStatus.ACTIVE);
		}

		@Override
		public CareModelMember confirmEnrollment(String uuid, String reason, Date asOf) {
			operation = "confirm";
			memberUuid = uuid;
			return member(MemberStatus.ACTIVE);
		}

		@Override
		public CareModelMember declineEnrollment(String uuid, String reason, Date asOf) {
			operation = "decline";
			memberUuid = uuid;
			return member(MemberStatus.DECLINED);
		}

		@Override
		public CareModelMember transitionEnrollment(String uuid, MemberStatus newStatus, String reason, Date asOf) {
			operation = "transition";
			memberUuid = uuid;
			status = newStatus;
			return member(newStatus);
		}

		private CareModelMember member(MemberStatus memberStatus) {
			CareModelMember member = new CareModelMember();
			member.setUuid("member-uuid");
			member.setStatus(memberStatus);
			member.setEpisodeNumber(1);
			return member;
		}

		@Override
		public CareModelMember applyRuleEnrollment(Integer patientId, Integer careModelId, String ruleId, Action action,
		        String sourceUuid, Date asOf) throws APIException {
			throw new UnsupportedOperationException();
		}

		@Override
		public int flagEnrollmentsForReview(Integer patientId, String sourceUuid, String reason, Date asOf)
		        throws APIException {
			throw new UnsupportedOperationException();
		}

		@Override
		public CareModelMember enrollManually(Integer patientId, Integer careModelId, String reason, Date asOf)
		        throws APIException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void onStartup() {
		}

		@Override
		public void onShutdown() {
		}
	}
}
