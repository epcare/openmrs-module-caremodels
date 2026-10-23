package org.openmrs.module.caremodels.api;

import java.util.Date;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.Test;
import org.openmrs.Privilege;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.model.*;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import static org.junit.Assert.*;

public class MemberTransitionAuditTest extends BaseModuleContextSensitiveTest {
	
	@Test
	public void transitionMustBeAuditedIdempotentAndNeverReopenClosedEpisodes() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode definition = (ObjectNode) mapper.readTree(getClass().getResourceAsStream("/samples/minimal-model.json"));
		((ObjectNode) definition.path("enrollment").path("manualEnrollment")).put("enabled", true);
		Context.getUserService().savePrivilege(new Privilege("View audit model", "Test"));
		CareModelService models = Context.getService(CareModelService.class);
		CareModel model = models.createCareModel("AUDIT_TRANSITION", "Audit", null, "View audit model",
		    definition.toString());
		models.publishCareModel(model.getCode(), "1.0.0", 1);
		CareModelMember member = Context.getService(CareModelEnrollmentService.class).enrollManually(2, model.getId(),
		    "Initial", new Date(1000));
		ObjectNode metadata = (ObjectNode) mapper.readTree(member.getMembershipData());
		metadata.putObject("pathway").put("stage", "review").put("enteredAt", "1970-01-01T00:00:01Z");
		member.setMembershipData(metadata.toString());
		CareModelMemberService members = Context.getService(CareModelMemberService.class);
		members.saveMember(member);
		members.transitionMember(member, CareModelMember.MemberStatus.SUSPENDED, "Pause", new Date(2000));
		members.transitionMember(member, CareModelMember.MemberStatus.SUSPENDED, "Retry", new Date(2000));
		members.transitionMember(member, CareModelMember.MemberStatus.ACTIVE, "Resume", new Date(3000));
		members.transitionMember(member, CareModelMember.MemberStatus.COMPLETED, "Complete", new Date(4000));
		JsonNode result = mapper.readTree(member.getMembershipData());
		assertEquals(4, result.path("history").size());
		assertEquals("RESUMED", result.path("history").get(2).path("type").asText());
		assertEquals("review", result.path("pathway").path("stage").asText());
		assertEquals(new Date(4000), member.getDateCompleted());
		try {
			members.transitionMember(member, CareModelMember.MemberStatus.ACTIVE, "Reopen", new Date(5000));
			fail();
		}
		catch (APIException expected) {}
		assertEquals(CareModelMember.MemberStatus.COMPLETED, member.getStatus());
	}
}
