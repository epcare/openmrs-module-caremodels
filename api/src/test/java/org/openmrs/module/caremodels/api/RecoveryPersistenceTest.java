package org.openmrs.module.caremodels.api;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.api.impl.MembershipHistory;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.Assert.*;

public class RecoveryPersistenceTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	private CareModelDAO dao;
	
	@Test
	public void recoveryQueriesMustBoundAndOrderPatientsAndLockCoreCheckpoint() {
		int upper = dao.getMaximumPatientId();
		List<Integer> first = dao.getPatientIdsAfter(0, upper, 1);
		assertEquals(1, first.size());
		List<Integer> next = dao.getPatientIdsAfter(first.get(0), upper, 1);
		assertEquals(1, next.size());
		assertTrue(next.get(0) > first.get(0));
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty("caremodels.reconciliation.cursor", "{}"));
		assertEquals("{}", dao.getReconciliationCursorForUpdate().getPropertyValue());
		assertTrue(dao.getEnrollmentModelsAfter(0, 1).isEmpty());
	}
	
	@Test
	public void reviewHistoryMustRemainBoundedAndPreserveRecordedPathway() throws Exception {
		CareModelMember member = new CareModelMember();
		member.setMembershipData("{\"enrollment\":{\"definitionVersion\":\"1.0.0\"},\"pathway\":{\"stage\":\"review\"}}");
		for (int i = 0; i < 105; i++)
			MembershipHistory
			        .append(member, "1.0.0", "FLAGGED_FOR_REVIEW", new Date(i), "Review", "source-" + i, null, true);
		JsonNode stored = new ObjectMapper().readTree(member.getMembershipData());
		assertEquals(100, stored.path("history").size());
		assertEquals("source-5", stored.path("history").get(0).path("sourceUuid").asText());
		assertEquals("review", stored.path("pathway").path("stage").asText());
		assertFalse(MembershipHistory.append(member, "1.0.0", "FLAGGED_FOR_REVIEW", new Date(105), "Review", "source-104",
		    null, true));
	}
}
