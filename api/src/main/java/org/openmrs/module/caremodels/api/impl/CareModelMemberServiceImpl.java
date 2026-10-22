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

import java.util.Date;
import java.util.List;

import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.caremodels.api.CareModelEnrollmentService;
import org.openmrs.module.caremodels.api.CareModelMemberService;
import org.openmrs.module.caremodels.api.db.CareModelMemberDAO;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.model.CareModelMember.MemberStatus;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link CareModelMemberService}.
 */
public class CareModelMemberServiceImpl extends BaseOpenmrsService implements CareModelMemberService {
	
	private CareModelMemberDAO dao;
	
	private CareModelEnrollmentService enrollmentService;

	public CareModelMemberDAO getDao() {
		return dao;
	}
	
	public void setDao(CareModelMemberDAO dao) {
		this.dao = dao;
	}
	
	public void setEnrollmentService(CareModelEnrollmentService enrollmentService) {
		this.enrollmentService = enrollmentService;
	}

	@Override
	@Transactional(readOnly = true)
	public CareModelMember getMember(Integer careModelMemberId) throws APIException {
		return dao.getCareModelMemberById(careModelMemberId);
	}
	
	@Override
	@Transactional(readOnly = true)
	public CareModelMember getMemberByUuid(String uuid) throws APIException {
		return dao.getCareModelMemberByUuid(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public CareModelMember getCurrentMember(Integer patientId, Integer careModelId) throws APIException {
		return dao.getCurrentMember(patientId, careModelId);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<CareModelMember> getMembersByPatientAndModel(Integer patientId, Integer careModelId) throws APIException {
		return dao.getMembersByPatientAndModel(patientId, careModelId);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<CareModelMember> getActiveMembersByPatient(Integer patientId) throws APIException {
		return dao.getActiveMembersByPatient(patientId);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<CareModelMember> getActiveMembersByModel(Integer careModelId) throws APIException {
		return dao.getActiveMembersByModel(careModelId);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<CareModelMember> getMembersByPatientAndStatus(Integer patientId, MemberStatus status, boolean includeVoided,
	        Integer startIndex, Integer limit) throws APIException {
		return dao.getMembersByPatientAndStatus(patientId, status, includeVoided, startIndex, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public long getMembersCount(Integer patientId, MemberStatus status, boolean includeVoided) throws APIException {
		return dao.getMembersCount(patientId, status, includeVoided);
	}
	
	@Override
	@Transactional
	public CareModelMember saveMember(CareModelMember member) throws APIException {
		if (member.getCareModelId() == null || member.getPatientId() == null) {
			throw new APIException("Care Model membership requires careModelId and patientId");
		}
		if (member.getUuid() == null) {
			member.setUuid(java.util.UUID.randomUUID().toString());
		}
		return dao.saveCareModelMember(member);
	}
	
	@Override
	@Transactional
	public CareModelMember transitionMember(CareModelMember member, MemberStatus newStatus, String reason, Date asOf)
	        throws APIException {
		if (member == null || member.getUuid() == null) {
			throw new APIException("A persisted Care Model membership is required");
		}
		return enrollmentService.transitionEnrollment(member.getUuid(), newStatus, reason, asOf);
	}
	
	@Override
	@Transactional
	public CareModelMember voidMember(CareModelMember member, String reason) throws APIException {
		dao.voidCareModelMember(member, reason);
		return member;
	}
	
	@Override
	@Transactional
	public CareModelMember unvoidMember(CareModelMember member) throws APIException {
		dao.unvoidCareModelMember(member);
		return member;
	}
	
	@Override
	@Transactional
	public void purgeMember(CareModelMember member) throws APIException {
		dao.purgeCareModelMember(member);
	}
}
