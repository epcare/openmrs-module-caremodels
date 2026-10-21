/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.api.db.hibernate;

import java.util.List;

import org.hibernate.Criteria;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.caremodels.api.db.CareModelMemberDAO;
import org.openmrs.module.caremodels.model.CareModelMember;
import org.openmrs.module.caremodels.model.CareModelMember.MemberStatus;

/**
 * Hibernate implementation of {@link CareModelMemberDAO}.
 */
public class HibernateCareModelMemberDAO implements CareModelMemberDAO {
	
	private DbSessionFactory sessionFactory;
	
	public DbSessionFactory getSessionFactory() {
		return sessionFactory;
	}
	
	public void setSessionFactory(DbSessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	@Override
	public CareModelMember saveCareModelMember(CareModelMember member) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(member);
		return member;
	}
	
	@Override
	public CareModelMember getCareModelMemberById(Integer id) {
		return (CareModelMember) sessionFactory.getCurrentSession().get(CareModelMember.class, id);
	}
	
	@Override
	public CareModelMember getCareModelMemberByUuid(String uuid) {
		return (CareModelMember) sessionFactory.getCurrentSession().createCriteria(CareModelMember.class)
		        .add(Restrictions.eq("uuid", uuid)).uniqueResult();
	}
	
	@Override
	public CareModelMember getCurrentMember(Integer patientId, Integer careModelId) {
		return (CareModelMember) sessionFactory.getCurrentSession().createCriteria(CareModelMember.class)
		        .add(Restrictions.eq("patientId", patientId)).add(Restrictions.eq("careModelId", careModelId))
		        .add(Restrictions.eq("voided", false))
		        .add(Restrictions.in("status", MemberStatus.ACTIVE, MemberStatus.SUSPENDED))
		        .addOrder(Order.desc("episodeNumber")).setMaxResults(1).uniqueResult();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<CareModelMember> getMembersByPatientAndModel(Integer patientId, Integer careModelId) {
		return sessionFactory.getCurrentSession().createCriteria(CareModelMember.class)
		        .add(Restrictions.eq("patientId", patientId)).add(Restrictions.eq("careModelId", careModelId))
		        .add(Restrictions.eq("voided", false)).addOrder(Order.desc("episodeNumber")).list();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<CareModelMember> getActiveMembersByPatient(Integer patientId) {
		return sessionFactory.getCurrentSession().createCriteria(CareModelMember.class)
		        .add(Restrictions.eq("patientId", patientId)).add(Restrictions.eq("status", MemberStatus.ACTIVE))
		        .add(Restrictions.eq("voided", false)).addOrder(Order.asc("careModelId")).list();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<CareModelMember> getActiveMembersByModel(Integer careModelId) {
		return sessionFactory.getCurrentSession().createCriteria(CareModelMember.class)
		        .add(Restrictions.eq("careModelId", careModelId)).add(Restrictions.eq("status", MemberStatus.ACTIVE))
		        .add(Restrictions.eq("voided", false)).list();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<CareModelMember> getMembersByPatientAndStatus(Integer patientId, MemberStatus status, boolean includeVoided,
	        Integer startIndex, Integer limit) {
		Criteria criteria = buildPatientStatusCriteria(patientId, status, includeVoided);
		if (startIndex != null) {
			criteria.setFirstResult(startIndex);
		}
		if (limit != null) {
			criteria.setMaxResults(limit);
		}
		criteria.addOrder(Order.desc("episodeNumber"));
		return criteria.list();
	}
	
	@Override
	public long getMembersCount(Integer patientId, MemberStatus status, boolean includeVoided) {
		Criteria criteria = buildPatientStatusCriteria(patientId, status, includeVoided);
		criteria.setProjection(Projections.rowCount());
		return (Long) criteria.uniqueResult();
	}
	
	private Criteria buildPatientStatusCriteria(Integer patientId, MemberStatus status, boolean includeVoided) {
		Criteria criteria = sessionFactory.getCurrentSession().createCriteria(CareModelMember.class);
		criteria.add(Restrictions.eq("patientId", patientId));
		if (status != null) {
			criteria.add(Restrictions.eq("status", status));
		}
		if (!includeVoided) {
			criteria.add(Restrictions.eq("voided", false));
		}
		return criteria;
	}
	
	@Override
	public void voidCareModelMember(CareModelMember member, String reason) throws DAOException {
		member.setVoided(true);
		member.setVoidReason(reason);
		member.setDateVoided(new java.util.Date());
		member.setVoidedBy(org.openmrs.api.context.Context.getAuthenticatedUser());
		sessionFactory.getCurrentSession().saveOrUpdate(member);
	}
	
	@Override
	public void unvoidCareModelMember(CareModelMember member) throws DAOException {
		member.setVoided(false);
		member.setVoidReason(null);
		member.setDateVoided(null);
		member.setVoidedBy(null);
		sessionFactory.getCurrentSession().saveOrUpdate(member);
	}
	
	@Override
	public void purgeCareModelMember(CareModelMember member) throws DAOException {
		sessionFactory.getCurrentSession().delete(member);
	}
}
