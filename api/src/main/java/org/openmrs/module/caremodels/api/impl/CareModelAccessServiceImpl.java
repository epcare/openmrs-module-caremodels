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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.openmrs.Patient;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.caremodels.api.CareModelAccessService;
import org.openmrs.module.caremodels.api.CareModelMemberService;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.config.StorageEnvelope;
import org.openmrs.module.caremodels.dto.CareModelMembershipDTO;
import org.openmrs.module.caremodels.dto.CareModelRef;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.security.CareModelsPrivileges;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link CareModelAccessService}. Fails closed at every step; blank or
 * unregistered model privileges grant nobody access (not even the master-privilege holder, because
 * the fail-closed rule is per model).
 */
public class CareModelAccessServiceImpl extends BaseOpenmrsService implements CareModelAccessService {
	
	private ObjectMapper objectMapper = new ObjectMapper();
	
	private CareModelMemberService memberService;
	
	private CareModelDAO careModelDAO;
	
	public void setMemberService(CareModelMemberService memberService) {
		this.memberService = memberService;
	}
	
	public void setCareModelDAO(CareModelDAO careModelDAO) {
		this.careModelDAO = careModelDAO;
	}
	
	@Override
	@Transactional(readOnly = true)
	public void requireMasterPrivilege() throws APIException {
		if (!Context.hasPrivilege(CareModelsPrivileges.VIEW_CARE_MODELS)) {
			throw new APIException("Access to Care Models requires the " + CareModelsPrivileges.VIEW_CARE_MODELS
			        + " privilege");
		}
	}
	
	@Override
	@Transactional(readOnly = true)
	public String requirePatientAccess(String patientUuid) throws APIException {
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		if (patient == null || patient.getVoided()) {
			throw new APIException("Patient not found");
		}
		// existing OpenMRS patient-access rules remain authoritative for clinical data; accept the
		// broad or the granular core patient-read privilege
		if (!Context.hasPrivilege("View Patients") && !Context.hasPrivilege("Get Patients")) {
			throw new APIException("Access to the patient record requires a patient-view privilege");
		}
		return patient.getUuid();
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<CareModelMembershipDTO> getAuthorizedMemberships(String patientUuid) throws APIException {
		// steps 1-2: master privilege and patient access
		requireMasterPrivilege();
		String resolvedPatientUuid = requirePatientAccess(patientUuid);
		
		Patient patient = Context.getPatientService().getPatientByUuid(resolvedPatientUuid);
		
		// step 3: indexed active-membership lookup; no rule evaluation and no resolver calls here
		List<CareModelMember> members = memberService.getActiveMembersByPatient(patient.getPatientId());
		if (members.isEmpty()) {
			return new ArrayList<CareModelMembershipDTO>();
		}
		
		// step 4: join model metadata (projection; configuration is never selected)
		List<Integer> modelIds = new ArrayList<Integer>();
		Map<Integer, CareModelMember> memberByModelId = new HashMap<Integer, CareModelMember>();
		for (CareModelMember member : members) {
			modelIds.add(member.getCareModelId());
			memberByModelId.put(member.getCareModelId(), member);
		}
		List<CareModelRef> modelRefs = careModelDAO.getModelRefs(modelIds);
		
		List<CareModelMembershipDTO> result = new ArrayList<CareModelMembershipDTO>();
		for (CareModelRef modelRef : modelRefs) {
			if (!isModelVisible(modelRef)) {
				continue;
			}
			CareModelMember member = memberByModelId.get(modelRef.getCareModelId());
			CareModelMembershipDTO dto = new CareModelMembershipDTO();
			dto.setMemberUuid(member.getUuid());
			dto.setModelUuid(modelRef.getUuid());
			dto.setModelCode(modelRef.getCode());
			dto.setModelName(modelRef.getName());
			// the patient-friendly label inside the definition JSON is deliberately NOT read here:
			// the membership path must not select the configuration envelope, so the safe name
			// column is used as the display label
			dto.setDisplayLabel(modelRef.getName());
			dto.setEpisodeNumber(member.getEpisodeNumber());
			dto.setDateEnrolled(member.getDateEnrolled());
			result.add(dto);
		}
		return result;
	}
	
	private boolean isModelVisible(CareModelRef modelRef) {
		// fail closed: only PUBLISHED, non-retired models whose named privilege the user holds
		if (Boolean.TRUE.equals(modelRef.getRetired())) {
			return false;
		}
		if (!CareModelStatus.PUBLISHED.name().equals(modelRef.getStatus())) {
			return false;
		}
		String privilege = modelRef.getPrivilege();
		if (privilege == null || privilege.trim().isEmpty()) {
			// blank privilege never means public
			return false;
		}
		return Context.hasPrivilege(privilege);
	}
	
	@Override
	@Transactional(readOnly = true)
	public AuthorizedAccess authorizeWorkspace(String patientUuid, String modelCodeOrUuid, String tabId) throws APIException {
		// full chain, revalidated on EVERY workspace/data request
		requireMasterPrivilege();
		String resolvedPatientUuid = requirePatientAccess(patientUuid);
		Patient patient = Context.getPatientService().getPatientByUuid(resolvedPatientUuid);
		
		CareModel model = findModel(modelCodeOrUuid);
		if (model == null) {
			// do not disclose whether the model exists
			throw new APIException("Care Model not found or not authorized");
		}
		if (!isModelVisible(toRef(model))) {
			throw new APIException("Care Model not found or not authorized");
		}
		
		CareModelMember member = memberService.getCurrentMember(patient.getPatientId(), model.getId());
		if (member == null || member.getVoided() || member.getStatus() != CareModelMember.MemberStatus.ACTIVE) {
			throw new APIException("No active membership in this Care Model");
		}
		
		// only now is the published definition loaded, and only for this model
		StorageEnvelope envelope;
		try {
			envelope = StorageEnvelope.fromJson(objectMapper, model.getConfiguration());
		}
		catch (IllegalStateException e) {
			throw new APIException("Stored Care Model envelope is not valid JSON", e);
		}
		if (envelope.getPublished() == null || envelope.getPublished().getDefinition() == null) {
			throw new APIException("Care Model not found or not authorized");
		}
		
		JsonNode published = envelope.getPublished().getDefinition();
		if (!published.isObject())
			throw new APIException("Care Model not found or not authorized");
		
		JsonNode workspaceNode = published.path("workspace");
		String resolvedTabId = tabId;
		if (resolvedTabId == null) {
			resolvedTabId = workspaceNode.path("defaultTab").asText(null);
			if (resolvedTabId == null || resolvedTabId.isEmpty()) {
				JsonNode firstTab = workspaceNode.path("tabs").path(0).path("id");
				resolvedTabId = firstTab.isMissingNode() ? null : firstTab.asText(null);
			}
		}
		JsonNode tab = resolvedTabId == null ? null : findTab(published, resolvedTabId);
		if (tab == null) {
			throw new APIException("Requested tab is not part of this Care Model");
		}
		
		if (tab.has("visibilityRule")) {
			org.openmrs.module.caremodels.event.EnrollmentRuleEvidenceFactory evidence = Context.getRegisteredComponent(
			    "caremodels.EnrollmentRuleEvidenceFactory",
			    org.openmrs.module.caremodels.event.EnrollmentRuleEvidenceFactory.class);
			java.util.Date asOf = new java.util.Date();
			if (evidence == null
			        || new org.openmrs.module.caremodels.rule.RuleExpressionEvaluator().evaluate(tab.path("visibilityRule"),
			            evidence.create(patient.getId(), published, asOf), asOf) != org.openmrs.module.caremodels.rule.TriState.TRUE)
				throw new APIException("Requested tab is not authorized");
		}
		
		// tab summary (id + label only): lets the frontend render the inner-tab strip without
		// ever receiving widget definitions of tabs it has not opened
		com.fasterxml.jackson.databind.node.ArrayNode tabs = new com.fasterxml.jackson.databind.node.ArrayNode(
		        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
		for (JsonNode authoredTab : workspaceNode.path("tabs")) {
			com.fasterxml.jackson.databind.node.ObjectNode summary = tabs.addObject();
			summary.put("id", authoredTab.path("id").asText());
			summary.put("label", authoredTab.path("label").asText());
		}
		
		AuthorizedAccess authorized = new AuthorizedAccess(resolvedPatientUuid, model.getCode(), model.getUuid(),
		        member.getUuid(), published, tab, tabs);
		boolean pathwayRequested = false;
		for (JsonNode widget : tab.path("widgets"))
			pathwayRequested |= "care-plan".equals(widget.path("type").asText());
		if (pathwayRequested)
			authorized.setMemberPathway(readMemberPathway(member, published));
		return authorized;
	}
	
	private JsonNode readMemberPathway(CareModelMember member, JsonNode published) {
        com.fasterxml.jackson.databind.node.ObjectNode result = objectMapper.createObjectNode().put("status", "insufficient-data");
        if (!published.path("pathways").path("enabled").asBoolean()) return result.put("reason", "pathway-not-enabled");
        try {
            if (member.getMembershipData() == null) return result.put("reason", "no-recorded-stage");
            JsonNode metadata = objectMapper.readTree(member.getMembershipData());
            if (!new org.openmrs.module.caremodels.config.JsonSchemaValidator().validate(metadata,
                org.openmrs.module.caremodels.config.JsonSchemaValidator.SchemaType.MEMBERSHIP_DATA).isValid())
                return result.put("reason", "invalid-membership-metadata");
            JsonNode pathway = metadata.path("pathway");
            for (JsonNode stage : published.path("pathways").path("stages")) {
                if (stage.path("id").asText().equals(pathway.path("stage").asText()) && pathway.hasNonNull("enteredAt")) {
                    if (java.time.Instant.parse(pathway.path("enteredAt").asText()).isAfter(java.time.Instant.now()))
                        return result.put("reason", "future-stage-date");
                    return result.put("status", "recorded").put("stage", stage.path("id").asText())
                        .put("label", stage.path("label").asText()).put("enteredAt", pathway.path("enteredAt").asText())
                        .put("provenance", "care_model_member.membership_data.pathway");
                }
            }
        } catch (IOException | IllegalArgumentException | java.time.format.DateTimeParseException e) {
            return result.put("reason", "invalid-membership-metadata");
        }
        return result.put("reason", "no-recorded-stage");
    }
	
	private JsonNode findTab(JsonNode published, String tabId) {
		JsonNode tabs = published.path("workspace").path("tabs");
		for (JsonNode tab : tabs) {
			if (tabId.equals(tab.path("id").asText(null))) {
				return tab;
			}
		}
		return null;
	}
	
	private CareModel findModel(String codeOrUuid) {
		if (codeOrUuid == null || codeOrUuid.trim().isEmpty()) {
			return null;
		}
		String value = codeOrUuid.trim();
		if (value.matches("^[0-9a-fA-F-]{36}$")) {
			return careModelDAO.getCareModelByUuid(value);
		}
		return careModelDAO.getCareModelByCode(value);
	}
	
	private CareModelRef toRef(CareModel model) {
		CareModelRef ref = new CareModelRef();
		ref.setCareModelId(model.getId());
		ref.setUuid(model.getUuid());
		ref.setCode(model.getCode());
		ref.setName(model.getName());
		ref.setPrivilege(model.getPrivilege());
		ref.setStatus(model.getStatus() != null ? model.getStatus().name() : null);
		ref.setRetired(model.getRetired());
		return ref;
	}
}
