/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.event;

import java.util.Collections;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.openmrs.Concept;
import org.openmrs.ConceptNumeric;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientProgram;
import org.openmrs.PatientState;
import org.openmrs.Program;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.api.db.CareModelMemberDAO;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.rule.RuleEvidence;
import org.openmrs.module.caremodels.rule.RuleEvidenceProvider;

/** Builds fail-closed rule evidence from OpenMRS clinical services. */
public class OpenmrsEnrollmentRuleEvidenceFactory implements EnrollmentRuleEvidenceFactory {

	private CareModelDAO careModelDAO;

	private CareModelMemberDAO memberDAO;

	public void setCareModelDAO(CareModelDAO careModelDAO) {
		this.careModelDAO = careModelDAO;
	}

	public void setMemberDAO(CareModelMemberDAO memberDAO) {
		this.memberDAO = memberDAO;
	}

	@Override
	public Integer resolvePatientId(String patientUuid) {
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		return patient == null || Boolean.TRUE.equals(patient.getVoided()) ? null : patient.getPatientId();
	}

	@Override
	public RuleEvidenceProvider create(Integer patientId, JsonNode definition, Date asOf) {
		return new Provider(patientId, definition, asOf == null ? new Date() : new Date(asOf.getTime()));
	}

	private final class Provider implements RuleEvidenceProvider {

		private final Integer patientId;

		private final JsonNode definition;

		private final Date asOf;

		private final Map<String, RuleEvidence> cache = new HashMap<String, RuleEvidence>();

		private Provider(Integer patientId, JsonNode definition, Date asOf) {
			this.patientId = patientId;
			this.definition = definition;
			this.asOf = asOf;
		}

		@Override
		public RuleEvidence resolve(String type, String alias) {
			if (type == null || alias == null || alias.trim().isEmpty()) {
				return RuleEvidence.unknown();
			}
			String key = type + ":" + alias;
			RuleEvidence cached = cache.get(key);
			if (cached != null) {
				return cached;
			}
			RuleEvidence resolved = resolveUncached(type, alias);
			cache.put(key, resolved);
			return resolved;
		}

		private RuleEvidence resolveUncached(String type, String alias) {
			if ("observation".equals(type)) {
				return observation(alias);
			}
			if ("encounter".equals(type)) {
				return encounter(alias);
			}
			if ("program".equals(type)) {
				return program(alias);
			}
			if ("enrollment".equals(type)) {
				return enrollment(alias);
			}
			return RuleEvidence.unknown();
		}

		private RuleEvidence observation(String alias) {
			String conceptUuid = terminologyUuid("concepts", alias);
			Concept concept = conceptUuid == null ? null : Context.getConceptService().getConceptByUuid(conceptUuid);
			Patient patient = Context.getPatientService().getPatient(patientId);
			if (concept == null || concept.isRetired() || patient == null || Boolean.TRUE.equals(patient.getVoided())) {
				return RuleEvidence.unknown();
			}
			List<Obs> observations = Context.getObsService().getObservations(Collections.singletonList(patient), null,
			    Collections.singletonList(concept), null, null, null, Arrays.asList("obsDatetime desc", "uuid asc"), 1,
			    null, null, asOf, false);
			if (observations.isEmpty()) {
				return RuleEvidence.absent();
			}
			Obs observation = observations.get(0);
			if (Boolean.TRUE.equals(observation.getVoided()) || !patientId.equals(observation.getPersonId())
			        || !concept.equals(observation.getConcept()) || observation.getObsDatetime() == null
			        || observation.getObsDatetime().after(asOf)) {
				return RuleEvidence.unknown();
			}
			ConceptNumeric numeric = Context.getConceptService().getConceptNumeric(concept.getConceptId());
			return RuleEvidence.present(observationValue(observation), observation.getObsDatetime(), numeric == null ? null
			        : numeric.getUnits());
		}

		private RuleEvidence encounter(String alias) {
			String typeUuid = terminologyUuid("encounterTypes", alias);
			EncounterType type = typeUuid == null ? null : Context.getEncounterService().getEncounterTypeByUuid(typeUuid);
			Patient patient = Context.getPatientService().getPatient(patientId);
			if (type == null || type.isRetired() || patient == null || Boolean.TRUE.equals(patient.getVoided())) {
				return RuleEvidence.unknown();
			}
			List<Encounter> encounters = Context.getEncounterService().getEncounters(patient, null, null, asOf, null,
			    Collections.singletonList(type), null, null, null, false);
			Encounter latest = null;
			for (Encounter candidate : encounters) {
				if (!Boolean.TRUE.equals(candidate.getVoided())
				        && patientId.equals(candidate.getPatient().getId())
				        && type.equals(candidate.getEncounterType())
				        && candidate.getEncounterDatetime() != null
				        && !candidate.getEncounterDatetime().after(asOf)
				        && (latest == null || later(candidate.getEncounterDatetime(), candidate.getUuid(),
				            latest.getEncounterDatetime(), latest.getUuid()))) {
					latest = candidate;
				}
			}
			return latest == null ? RuleEvidence.absent() : RuleEvidence.present(latest.getUuid(),
			    latest.getEncounterDatetime(), null);
		}

		private RuleEvidence program(String alias) {
			String programUuid = terminologyUuid("programs", alias);
			Program program = programUuid == null ? null : Context.getProgramWorkflowService().getProgramByUuid(programUuid);
			Patient patient = Context.getPatientService().getPatient(patientId);
			if (program == null || program.isRetired() || patient == null || Boolean.TRUE.equals(patient.getVoided())) {
				return RuleEvidence.unknown();
			}
			List<PatientProgram> programs = Context.getProgramWorkflowService().getPatientPrograms(patient, program, null,
			    asOf, null, null, false);
			PatientProgram latest = latestProgram(programs);
			if (latest == null) {
				return RuleEvidence.absent();
			}
			boolean completed = latest.getDateCompleted() != null && !latest.getDateCompleted().after(asOf);
			PatientState state = completed ? null : latestState(latest.getStates());
			Object value = state == null ? (completed ? "COMPLETED" : "ACTIVE") : codedAlias(state.getState().getConcept());
			Date effective = state == null ? latest.getDateEnrolled() : state.getStartDate();
			return RuleEvidence.present(value, effective, null);
		}

		private RuleEvidence enrollment(String modelCode) {
			CareModel model = careModelDAO.getCareModelByCode(modelCode);
			if (model == null || Boolean.TRUE.equals(model.getRetired())) {
				return RuleEvidence.unknown();
			}
			CareModelMember member = memberDAO.getCurrentMember(patientId, model.getId());
			return member == null ? RuleEvidence.absent() : RuleEvidence.present(member.getStatus().name(),
			    member.getDateEnrolled(), null);
		}

		private Object observationValue(Obs observation) {
			if (observation.getValueNumeric() != null) {
				String modifier = observation.getValueModifier();
				if (modifier != null && !modifier.trim().isEmpty() && !"=".equals(modifier.trim()))
					return modifier + observation.getValueNumeric();
				return Double.isFinite(observation.getValueNumeric()) ? observation.getValueNumeric() : null;
			}
			if (observation.getValueCoded() != null) {
				return codedAlias(observation.getValueCoded());
			}
			if (observation.getValueBoolean() != null) {
				return observation.getValueBoolean();
			}
			if (observation.getValueText() != null) {
				return observation.getValueText();
			}
			if (observation.getValueDatetime() != null) {
				return observation.getValueDatetime();
			}
			return null;
		}

		private String terminologyUuid(String kind, String alias) {
			JsonNode value = definition.path("terminology").path(kind).get(alias);
			return value == null || !value.isTextual() ? null : value.asText();
		}

		private String codedAlias(Concept concept) {
			if (concept == null || concept.getUuid() == null) {
				return null;
			}
			JsonNode aliases = definition.path("terminology").path("codedAnswers");
			java.util.Iterator<Map.Entry<String, JsonNode>> fields = aliases.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> field = fields.next();
				if (concept.getUuid().equals(field.getValue().asText())) {
					return field.getKey();
				}
			}
			return concept.getUuid();
		}

		private PatientProgram latestProgram(List<PatientProgram> programs) {
			PatientProgram latest = null;
			for (PatientProgram candidate : programs) {
				if (!Boolean.TRUE.equals(candidate.getVoided())
				        && candidate.getDateEnrolled() != null
				        && !candidate.getDateEnrolled().after(asOf)
				        && patientId.equals(candidate.getPatient().getId())
				        && (latest == null || later(candidate.getDateEnrolled(), candidate.getUuid(),
				            latest.getDateEnrolled(), latest.getUuid()))) {
					latest = candidate;
				}
			}
			return latest;
		}

		private PatientState latestState(Set<PatientState> states) {
			PatientState latest = null;
			for (PatientState candidate : states) {
				if (!Boolean.TRUE.equals(candidate.getVoided())
				        && candidate.getStartDate() != null
				        && !candidate.getStartDate().after(asOf)
				        && (candidate.getEndDate() == null || candidate.getEndDate().after(asOf))
				        && (latest == null || later(candidate.getStartDate(), candidate.getUuid(), latest.getStartDate(),
				            latest.getUuid()))) {
					latest = candidate;
				}
			}
			return latest;
		}

		private boolean later(Date candidate, String candidateUuid, Date current, String currentUuid) {
			return candidate != null
			        && (current == null || candidate.after(current) || (candidate.equals(current) && candidateUuid != null && (currentUuid == null || candidateUuid
			                .compareTo(currentUuid) < 0)));
		}
	}
}
