/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.resolver;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.openmrs.*;
import org.openmrs.api.context.Context;

/** Exact program enrollment episodes and their recorded current states. */
public class ProgramStateResolver implements DataResolver {
	
	@Override
	public String getId() {
		return "program-state";
	}
	
	@Override
	public String getReferenceKind() {
		return "programs";
	}
	
	@Override
	public String getReferenceField() {
		return "programAlias";
	}
	
	@Override public JsonNode resolve(Integer patientId, JsonNode source, String reference) {
        Patient patient = ResolverSupport.patient(patientId);
        Program program = reference == null ? null : Context.getProgramWorkflowService().getProgramByUuid(reference);
        if (program == null || program.isRetired()) throw new IllegalArgumentException("Program unavailable");
        Instant now = Instant.now();
        int limit = ResolverSupport.limit(source, 100);
        Date since = ResolverSupport.since(source, now, 3650);
        List<PatientProgram> safe = new ArrayList<>();
        for (PatientProgram episode : Context.getProgramWorkflowService().getPatientPrograms(patient, program, since, Date.from(now), null, null, false)) {
            if (!Boolean.TRUE.equals(episode.getVoided()) && patient.equals(episode.getPatient()) && program.equals(episode.getProgram())
                    && ResolverSupport.inWindow(episode.getDateEnrolled(), since, now)) safe.add(episode);
        }
        safe.sort(ResolverSupport.newest(PatientProgram::getDateEnrolled, PatientProgram::getUuid));
        ObjectNode result = ResolverSupport.result(getId(), now).put("programUuid", program.getUuid());
        result.putArray("availableFields").add("uuid").add("dateEnrolled").add("dateCompleted").add("currentStates");
        ArrayNode records = result.putArray("records");
        for (PatientProgram episode : safe.subList(0, Math.min(limit, safe.size()))) {
            ObjectNode row = records.addObject().put("uuid", episode.getUuid()).put("dateEnrolled", ResolverSupport.time(episode.getDateEnrolled()))
                .put("dateCompleted", ResolverSupport.time(episode.getDateCompleted()));
            ArrayNode states = row.putArray("currentStates");
            List<PatientState> current = new ArrayList<>();
            if (episode.getDateCompleted() == null || episode.getDateCompleted().after(Date.from(now))) {
                for (PatientState state : episode.getStates()) {
                    if (!Boolean.TRUE.equals(state.getVoided()) && state.getStartDate() != null && !state.getStartDate().after(Date.from(now))
                        && (state.getEndDate() == null || state.getEndDate().after(Date.from(now)))) current.add(state);
                }
            }
            current.sort(Comparator.comparing(PatientState::getUuid));
            for (PatientState state : current) {
                if (states.size() >= 50) break;
                states.addObject().put("uuid", state.getUuid()).put("stateUuid", state.getState().getUuid())
                    .put("workflowUuid", state.getState().getProgramWorkflow().getUuid()).put("startDate", ResolverSupport.time(state.getStartDate()));
            }
            row.put("statesTruncated", current.size() > 50);
        }
        ResolverSupport.collection(result, records, safe.size(), limit);
        return result;
    }
}
