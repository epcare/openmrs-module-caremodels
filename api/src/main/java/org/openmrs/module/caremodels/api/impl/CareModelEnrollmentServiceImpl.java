/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.api.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.openmrs.Patient;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.caremodels.api.CareModelEnrollmentService;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.api.db.CareModelMemberDAO;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.model.CareModelMember.MemberStatus;
import org.openmrs.module.caremodels.rule.EnrollmentRuleEngine.Action;
import org.springframework.transaction.annotation.Transactional;

/** Default enrollment implementation. The locked model row serializes episode creation. */
public class CareModelEnrollmentServiceImpl extends BaseOpenmrsService implements CareModelEnrollmentService {
	
	private static final int MAX_HISTORY = 100;
	
	private static final String SOURCE_AUTO = "AUTO";
	
	private static final String SOURCE_CONFIRMATION = "CONFIRMATION";
	
	private static final String SOURCE_MANUAL = "MANUAL";
	
	private static final String SOURCE_BACKFILL = "BACKFILL";
	
	private CareModelDAO careModelDAO;
	
	private CareModelMemberDAO memberDAO;
	
	private ObjectMapper objectMapper = new ObjectMapper();
	
	private EnrollmentActorProvider actorProvider = new OpenmrsEnrollmentActorProvider();
	
	private EnrollmentCommandAuthorizer commandAuthorizer = new OpenmrsEnrollmentCommandAuthorizer();
	
	public void setCareModelDAO(CareModelDAO careModelDAO) {
		this.careModelDAO = careModelDAO;
	}
	
	public void setMemberDAO(CareModelMemberDAO memberDAO) {
		this.memberDAO = memberDAO;
	}
	
	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}
	
	public void setActorProvider(EnrollmentActorProvider actorProvider) {
		this.actorProvider = actorProvider;
	}
	
	public void setCommandAuthorizer(EnrollmentCommandAuthorizer commandAuthorizer) {
		this.commandAuthorizer = commandAuthorizer;
	}
	
	@Override
	@Transactional
	public CareModelMember applyRuleEnrollment(Integer patientId, Integer careModelId, String ruleId, Action action,
	        String sourceUuid, Date asOf) throws APIException {
		return applyRuleEnrollment(patientId, careModelId, ruleId, action, sourceUuid, asOf, false);
	}
	
	@Override
	@Transactional
	public CareModelMember applyBackfillEnrollment(Integer patientId, Integer careModelId, String ruleId, Action action,
	        String sourceUuid, Date asOf) throws APIException {
		return applyRuleEnrollment(patientId, careModelId, ruleId, action, sourceUuid, asOf, true);
	}
	
	private CareModelMember applyRuleEnrollment(Integer patientId, Integer careModelId, String ruleId, Action action,
	        String sourceUuid, Date asOf, boolean backfill) {
		assertIds(patientId, careModelId);
		assertText(ruleId, "Rule id is required");
		if (ruleId.length() > 100) {
			throw new APIException("Rule id must be at most 100 characters");
		}
		assertSourceUuid(sourceUuid);
		if (action != Action.AUTO_ENROLL && action != Action.PROPOSE_CONFIRMATION) {
			throw new APIException("Only AUTO_ENROLL and PROPOSE_CONFIRMATION create membership episodes");
		}
		
		PublishedModel published = lockModel(careModelId);
		CareModelMember replay = memberDAO.getMemberBySource(patientId, careModelId, sourceUuid);
		if (replay != null) {
			return replay;
		}
		assertRuleEnabled(published.definition, ruleId, action);
		
		Date effectiveAt = effectiveDate(asOf);
		CareModelMember open = memberDAO.getOpenMember(patientId, careModelId);
		if (open != null) {
			if (open.getStatus() == MemberStatus.PENDING && action == Action.AUTO_ENROLL) {
				String source = backfill ? SOURCE_BACKFILL : SOURCE_AUTO;
				activate(open, source, ruleId, published.version, sourceUuid, effectiveAt, null);
				return memberDAO.saveCareModelMember(open);
			}
			return open;
		}
		
		if (!canReenroll(patientId, careModelId, published.definition, effectiveAt))
			return null;
		
		MemberStatus status = action == Action.AUTO_ENROLL ? MemberStatus.ACTIVE : MemberStatus.PENDING;
		String source = action == Action.AUTO_ENROLL ? (backfill ? SOURCE_BACKFILL : SOURCE_AUTO) : SOURCE_CONFIRMATION;
		String triggerType = backfill ? SOURCE_BACKFILL : source;
		String historyType = status == MemberStatus.ACTIVE ? "ENROLLED" : "EPISODE_STARTED";
		CareModelMember member = newMember(patientId, careModelId, status, source, sourceUuid,
		    memberDAO.getNextEpisodeNumber(patientId, careModelId), effectiveAt);
		member.setMembershipData(newMembershipData(published.version, ruleId, triggerType, sourceUuid, historyType,
		    effectiveAt, null));
		return memberDAO.saveCareModelMember(member);
	}
	
	@Override
	@Transactional
	public int flagEnrollmentsForReview(Integer patientId, String sourceUuid, String reason, Date asOf) throws APIException {
		if (patientId == null) {
			throw new APIException("Patient id is required");
		}
		assertSourceUuid(sourceUuid);
		assertReason(reason);
		Date effectiveAt = effectiveDate(asOf);
		int flagged = 0;
		for (CareModelMember member : memberDAO.getMembersBySourceForUpdate(patientId, sourceUuid)) {
			if (hasReviewFlag(member.getMembershipData(), sourceUuid)) {
				continue;
			}
			member.setMembershipData(appendHistory(member.getMembershipData(), "FLAGGED_FOR_REVIEW", effectiveAt, reason,
			    sourceUuid));
			memberDAO.saveCareModelMember(member);
			flagged++;
		}
		return flagged;
	}
	
	@Override
	@Transactional
	public CareModelMember confirmEnrollment(String memberUuid, String reason, Date asOf) throws APIException {
		assertText(memberUuid, "Membership UUID is required");
		assertReason(reason);
		CareModelMember member = getMember(memberUuid);
		PublishedModel published = lockModel(member.getCareModelId());
		authorizeCommand(member.getPatientId(), published.model);
		member = getMember(memberUuid);
		if (member.getStatus() == MemberStatus.ACTIVE && SOURCE_CONFIRMATION.equals(member.getEnrollmentSource())) {
			return member;
		}
		assertPendingConfirmation(member);
		CareModelMember current = memberDAO.getCurrentMember(member.getPatientId(), member.getCareModelId());
		if (current != null && !current.getUuid().equals(member.getUuid())) {
			return current;
		}
		Date effectiveAt = effectiveDate(asOf);
		member.setStatus(MemberStatus.ACTIVE);
		member.setDateEnrolled(effectiveAt);
		member.setMembershipData(addConfirmation(member.getMembershipData(), effectiveAt, reason, member.getSourceUuid()));
		return memberDAO.saveCareModelMember(member);
	}
	
	@Override
	@Transactional
	public CareModelMember declineEnrollment(String memberUuid, String reason, Date asOf) throws APIException {
		assertText(memberUuid, "Membership UUID is required");
		assertReason(reason);
		CareModelMember member = getMember(memberUuid);
		PublishedModel published = lockModel(member.getCareModelId());
		authorizeCommand(member.getPatientId(), published.model);
		member = getMember(memberUuid);
		if (member.getStatus() == MemberStatus.DECLINED) {
			return member;
		}
		assertPendingConfirmation(member);
		Date effectiveAt = effectiveDate(asOf);
		member.setStatus(MemberStatus.DECLINED);
		member.setDateCompleted(effectiveAt);
		member.setMembershipData(appendHistory(member.getMembershipData(), "DECLINED", effectiveAt, reason,
		    member.getSourceUuid()));
		return memberDAO.saveCareModelMember(member);
	}
	
	@Override
	@Transactional
	public CareModelMember enrollManually(Integer patientId, Integer careModelId, String reason, Date asOf)
	        throws APIException {
		assertIds(patientId, careModelId);
		assertReason(reason);
		PublishedModel published = lockModel(careModelId);
		authorizeCommand(patientId, published.model);
		JsonNode manual = published.definition.path("enrollment").path("manualEnrollment");
		if (!manual.path("enabled").asBoolean(false)) {
			throw new APIException("Manual enrollment is disabled for this Care Model");
		}
		
		Date effectiveAt = effectiveDate(asOf);
		CareModelMember open = memberDAO.getOpenMember(patientId, careModelId);
		if (open != null) {
			if (open.getStatus() == MemberStatus.PENDING) {
				activate(open, SOURCE_MANUAL, null, published.version, null, effectiveAt, reason);
				return memberDAO.saveCareModelMember(open);
			}
			return open;
		}
		
		if (!canReenroll(patientId, careModelId, published.definition, effectiveAt))
			throw new APIException("Reenrollment is disabled or its cooldown has not elapsed");
		
		CareModelMember member = newMember(patientId, careModelId, MemberStatus.ACTIVE, SOURCE_MANUAL, null,
		    memberDAO.getNextEpisodeNumber(patientId, careModelId), effectiveAt);
		member.setMembershipData(newMembershipData(published.version, null, SOURCE_MANUAL, null, "ENROLLED", effectiveAt,
		    reason));
		return memberDAO.saveCareModelMember(member);
	}
	
	private boolean canReenroll(Integer patientId, Integer modelId, JsonNode definition, Date asOf) {
		java.util.List<CareModelMember> episodes = memberDAO.getMembersByPatientAndModel(patientId, modelId);
		CareModelMember latest = null;
		for (CareModelMember episode : episodes) {
			if (Boolean.TRUE.equals(episode.getVoided()))
				continue;
			if (latest == null || episode.getEpisodeNumber() > latest.getEpisodeNumber())
				latest = episode;
		}
		if (latest == null)
			return true;
		JsonNode policy = definition.path("enrollment").path("reenrollment");
		if (!policy.path("enabled").asBoolean(false) || latest.getDateCompleted() == null)
			return false;
		long cooldown = policy.path("cooldownDays").asLong(0);
		if (cooldown < 0 || cooldown > 3650)
			throw new APIException("Invalid reenrollment cooldown");
		return asOf.getTime() >= latest.getDateCompleted().getTime() + cooldown * 86400000L;
	}
	
	@Override
	@Transactional
	public CareModelMember enrollManually(String patientUuid, String modelCodeOrUuid, String reason, Date asOf)
	        throws APIException {
		assertText(patientUuid, "Patient UUID is required");
		assertText(modelCodeOrUuid, "Care Model code or UUID is required");
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid.trim());
		CareModel model = findModel(modelCodeOrUuid.trim());
		if (patient == null || Boolean.TRUE.equals(patient.getVoided()) || model == null) {
			throw new APIException("Patient or Care Model not found");
		}
		return enrollManually(patient.getPatientId(), model.getId(), reason, asOf);
	}
	
	@Override
	@Transactional
	public CareModelMember transitionEnrollment(String memberUuid, MemberStatus newStatus, String reason, Date asOf)
	        throws APIException {
		assertText(memberUuid, "Membership UUID is required");
		assertReason(reason);
		if (newStatus == null) {
			throw new APIException("Target membership status is required");
		}
		CareModelMember member = getMemberForUpdate(memberUuid);
		CareModel model = careModelDAO.getCareModelById(member.getCareModelId());
		if (model == null) {
			throw new APIException("Care Model not found: " + member.getCareModelId());
		}
		authorizeCommand(member.getPatientId(), model);
		if (member.getStatus() == newStatus) {
			return member;
		}
		assertTransition(member.getStatus(), newStatus);
		
		Date effectiveAt = effectiveDate(asOf);
		member.setStatus(newStatus);
		if (newStatus == MemberStatus.COMPLETED || newStatus == MemberStatus.WITHDRAWN) {
			member.setDateCompleted(effectiveAt);
		} else if (newStatus == MemberStatus.ACTIVE) {
			member.setDateCompleted(null);
		}
		member.setMembershipData(appendHistory(member.getMembershipData(), historyType(newStatus), effectiveAt, reason, null));
		return memberDAO.saveCareModelMember(member);
	}
	
	private PublishedModel lockModel(Integer careModelId) {
		CareModel model = careModelDAO.getCareModelByIdForUpdate(careModelId);
		if (model == null) {
			throw new APIException("Care Model not found: " + careModelId);
		}
		if (model.getStatus() != CareModelStatus.PUBLISHED || Boolean.TRUE.equals(model.getRetired())) {
			throw new APIException("Enrollment requires a published, non-retired Care Model");
		}
		StorageEnvelope envelope = StorageEnvelope.fromJson(objectMapper, model.getConfiguration());
		if (envelope.getPublished() == null || envelope.getPublished().getDefinition() == null) {
			throw new APIException("Care Model has no published definition");
		}
		assertText(envelope.getPublished().getVersion(), "Published definition version is missing");
		return new PublishedModel(model, envelope.getPublished().getVersion(), envelope.getPublished().getDefinition());
	}
	
	private CareModel findModel(String codeOrUuid) {
		return codeOrUuid.matches("^[0-9a-fA-F-]{36}$") ? careModelDAO.getCareModelByUuid(codeOrUuid) : careModelDAO
		        .getCareModelByCode(codeOrUuid);
	}
	
	private void authorizeCommand(Integer patientId, CareModel model) {
		if (commandAuthorizer == null) {
			throw new APIException("Care Model membership is not authorized");
		}
		commandAuthorizer.authorize(patientId, model);
	}
	
	private void assertTransition(MemberStatus current, MemberStatus target) {
		boolean allowed = current == MemberStatus.ACTIVE
		        && (target == MemberStatus.SUSPENDED || target == MemberStatus.COMPLETED || target == MemberStatus.WITHDRAWN)
		        || current == MemberStatus.SUSPENDED
		        && (target == MemberStatus.ACTIVE || target == MemberStatus.COMPLETED || target == MemberStatus.WITHDRAWN);
		if (!allowed) {
			throw new APIException("Membership transition from " + current + " to " + target + " is not allowed");
		}
	}
	
	private String historyType(MemberStatus status) {
		switch (status) {
			case ACTIVE:
				return "RESUMED";
			case SUSPENDED:
				return "SUSPENDED";
			case COMPLETED:
				return "COMPLETED";
			case WITHDRAWN:
				return "WITHDRAWN";
			default:
				throw new APIException("Unsupported membership transition: " + status);
		}
	}
	
	private void assertRuleEnabled(JsonNode definition, String ruleId, Action action) {
		JsonNode enrollment = definition.path("enrollment");
		if (!enrollment.path("enabled").asBoolean(false)) {
			throw new APIException("Rule-driven enrollment is disabled for this Care Model");
		}
		for (JsonNode rule : enrollment.path("rules")) {
			if (ruleId.equals(rule.path("id").asText(null)) && action.name().equals(rule.path("action").asText(null))) {
				return;
			}
		}
		throw new APIException("Rule '" + ruleId + "' is not a published " + action + " rule");
	}
	
	private CareModelMember newMember(Integer patientId, Integer careModelId, MemberStatus status, String source,
	        String sourceUuid, int episodeNumber, Date effectiveAt) {
		CareModelMember member = new CareModelMember();
		member.setUuid(UUID.randomUUID().toString());
		member.setPatientId(patientId);
		member.setCareModelId(careModelId);
		member.setStatus(status);
		member.setEpisodeNumber(episodeNumber);
		member.setEnrollmentSource(source);
		member.setSourceUuid(sourceUuid);
		if (status == MemberStatus.ACTIVE) {
			member.setDateEnrolled(effectiveAt);
		}
		return member;
	}
	
	private void activate(CareModelMember member, String source, String ruleId, String version, String sourceUuid,
	        Date effectiveAt, String reason) {
		member.setStatus(MemberStatus.ACTIVE);
		member.setDateEnrolled(effectiveAt);
		member.setDateCompleted(null);
		member.setEnrollmentSource(source);
		member.setSourceUuid(sourceUuid);
		member.setMembershipData(updateEnrollmentData(member.getMembershipData(), version, ruleId, source, sourceUuid,
		    effectiveAt, reason));
	}
	
	private String newMembershipData(String version, String ruleId, String triggerType, String sourceUuid,
	        String historyType, Date at, String reason) {
		ObjectNode root = objectMapper.createObjectNode();
		root.put("schemaVersion", "1.0.0");
		ObjectNode enrollment = root.putObject("enrollment");
		enrollment.put("definitionVersion", version);
		if (ruleId != null) {
			enrollment.put("ruleId", ruleId);
		}
		ObjectNode trigger = enrollment.putObject("trigger");
		trigger.put("type", triggerType);
		if (sourceUuid != null) {
			trigger.put("uuid", sourceUuid);
		}
		appendHistory(root.putArray("history"), historyType, at, reason, sourceUuid);
		return writeJson(root);
	}
	
	private String updateEnrollmentData(String json, String version, String ruleId, String triggerType, String sourceUuid,
	        Date at, String reason) {
		ObjectNode root = readMembershipData(json);
		ObjectNode enrollment = root.putObject("enrollment");
		enrollment.put("definitionVersion", version);
		if (ruleId != null) {
			enrollment.put("ruleId", ruleId);
		}
		ObjectNode trigger = enrollment.putObject("trigger");
		trigger.put("type", triggerType);
		if (sourceUuid != null) {
			trigger.put("uuid", sourceUuid);
		}
		appendHistory(history(root), "ENROLLED", at, reason, sourceUuid);
		return writeJson(root);
	}
	
	private String addConfirmation(String json, Date at, String reason, String sourceUuid) {
		ObjectNode root = readMembershipData(json);
		ObjectNode confirmation = root.with("enrollment").putObject("confirmation");
		String actor = currentActor();
		if (actor != null) {
			confirmation.put("confirmedBy", actor);
		}
		confirmation.put("confirmedAt", formatDate(at));
		appendHistory(history(root), "ENROLLED", at, reason, sourceUuid);
		return writeJson(root);
	}
	
	private String appendHistory(String json, String type, Date at, String reason, String sourceUuid) {
		ObjectNode root = readMembershipData(json);
		appendHistory(history(root), type, at, reason, sourceUuid);
		return writeJson(root);
	}
	
	private boolean hasReviewFlag(String json, String sourceUuid) {
		for (JsonNode event : history(readMembershipData(json))) {
			if ("FLAGGED_FOR_REVIEW".equals(event.path("type").asText())
			        && sourceUuid.equals(event.path("sourceUuid").asText(null))) {
				return true;
			}
		}
		return false;
	}
	
	private void appendHistory(ArrayNode history, String type, Date at, String reason, String sourceUuid) {
		while (history.size() >= MAX_HISTORY) {
			history.remove(0);
		}
		ObjectNode event = history.addObject();
		event.put("type", type);
		event.put("at", formatDate(at));
		String actor = currentActor();
		if (actor != null) {
			event.put("actor", actor);
		}
		if (reason != null) {
			event.put("reason", reason);
		}
		if (sourceUuid != null) {
			event.put("sourceUuid", sourceUuid);
		}
	}
	
	private ArrayNode history(ObjectNode root) {
		JsonNode history = root.get("history");
		return history instanceof ArrayNode ? (ArrayNode) history : root.putArray("history");
	}
	
	private ObjectNode readMembershipData(String json) {
		try {
			JsonNode parsed = objectMapper.readTree(json);
			if (!(parsed instanceof ObjectNode)) {
				throw new APIException("Membership provenance is not a JSON object");
			}
			return (ObjectNode) parsed;
		}
		catch (APIException e) {
			throw e;
		}
		catch (Exception e) {
			throw new APIException("Cannot parse membership provenance", e);
		}
	}
	
	private String writeJson(ObjectNode root) {
		try {
			return objectMapper.writeValueAsString(root);
		}
		catch (Exception e) {
			throw new APIException("Cannot serialize membership provenance", e);
		}
	}
	
	private CareModelMember getMember(String uuid) {
		CareModelMember member = memberDAO.getCareModelMemberByUuid(uuid);
		if (member == null || member.getVoided()) {
			throw new APIException("Care Model membership not found: " + uuid);
		}
		return member;
	}
	
	private CareModelMember getMemberForUpdate(String uuid) {
		CareModelMember member = memberDAO.getCareModelMemberByUuidForUpdate(uuid);
		if (member == null || Boolean.TRUE.equals(member.getVoided())) {
			throw new APIException("Care Model membership not found: " + uuid);
		}
		return member;
	}
	
	private void assertPendingConfirmation(CareModelMember member) {
		if (member.getStatus() != MemberStatus.PENDING || !SOURCE_CONFIRMATION.equals(member.getEnrollmentSource())) {
			throw new APIException("Only pending confirmation proposals can be confirmed or declined");
		}
	}
	
	private void assertIds(Integer patientId, Integer careModelId) {
		if (patientId == null || careModelId == null) {
			throw new APIException("patientId and careModelId are required");
		}
	}
	
	private void assertSourceUuid(String sourceUuid) {
		assertText(sourceUuid, "Source UUID is required for rule-driven enrollment");
		if (sourceUuid.length() > 38) {
			throw new APIException("Source UUID must be at most 38 characters");
		}
	}
	
	private void assertReason(String reason) {
		assertText(reason, "An audited reason is required");
		if (reason.length() > 500) {
			throw new APIException("Reason must be at most 500 characters");
		}
	}
	
	private void assertText(String value, String message) {
		if (value == null || value.trim().isEmpty()) {
			throw new APIException(message);
		}
	}
	
	private Date effectiveDate(Date asOf) {
		return asOf == null ? new Date() : new Date(asOf.getTime());
	}
	
	private String currentActor() {
		return actorProvider == null ? null : actorProvider.getCurrentActor();
	}
	
	private String formatDate(Date date) {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		return format.format(date);
	}
	
	private static final class PublishedModel {
		
		private final CareModel model;
		
		private final String version;
		
		private final JsonNode definition;
		
		private PublishedModel(CareModel model, String version, JsonNode definition) {
			this.model = model;
			this.version = version;
			this.definition = definition;
		}
	}
}
