/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.api.db;

import java.util.List;

import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.model.CareModelMember.MemberStatus;

/**
 * Database access for {@link CareModelMember} episodes. Active-episode uniqueness under concurrency
 * is additionally enforced by the generated-column unique index in Liquibase.
 */
public interface CareModelMemberDAO {
	
	CareModelMember saveCareModelMember(CareModelMember member);
	
	CareModelMember getCareModelMemberById(Integer id);
	
	CareModelMember getCareModelMemberByUuid(String uuid);
	
	/** Locks one membership episode for a serialized clinician transition. */
	CareModelMember getCareModelMemberByUuidForUpdate(String uuid);
	
	/** Finds a prior rule-driven enrollment for idempotent event replay. */
	CareModelMember getMemberBySource(Integer patientId, Integer careModelId, String sourceUuid);
	
	/** Locks every non-voided episode created from one clinical evidence source. */
	List<CareModelMember> getMembersBySourceForUpdate(Integer patientId, String sourceUuid);
	
	/** Current proposal or enrolled episode, excluding completed/declined/erroneous history. */
	CareModelMember getOpenMember(Integer patientId, Integer careModelId);
	
	/** Next episode counter including voided history, which still owns its unique episode number. */
	int getNextEpisodeNumber(Integer patientId, Integer careModelId);
	
	/** The current (non-voided, ACTIVE or SUSPENDED) episode for a patient in a model, if any. */
	CareModelMember getCurrentMember(Integer patientId, Integer careModelId);
	
	/** All non-voided episodes of a patient in a model, newest episode first. */
	List<CareModelMember> getMembersByPatientAndModel(Integer patientId, Integer careModelId);
	
	/** All ACTIVE non-voided episodes of a patient across models; the patient-tab source query. */
	List<CareModelMember> getActiveMembersByPatient(Integer patientId);
	
	/** All ACTIVE non-voided episodes in a model; enrollment/backfill support. */
	List<CareModelMember> getActiveMembersByModel(Integer careModelId);
	
	List<CareModelMember> getMembersByPatientAndStatus(Integer patientId, MemberStatus status, boolean includeVoided,
	        Integer startIndex, Integer limit);
	
	long getMembersCount(Integer patientId, MemberStatus status, boolean includeVoided);
	
	void voidCareModelMember(CareModelMember member, String reason);
	
	void unvoidCareModelMember(CareModelMember member);
	
	void purgeCareModelMember(CareModelMember member);
}
