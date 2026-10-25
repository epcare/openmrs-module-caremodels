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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.LockMode;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.caremodels.api.db.CareModelDAO;
import org.openmrs.module.caremodels.dto.CareModelRef;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;

/**
 * Hibernate implementation of {@link CareModelDAO}.
 */
public class HibernateCareModelDAO implements CareModelDAO {
	
	private DbSessionFactory sessionFactory;
	
	public DbSessionFactory getSessionFactory() {
		return sessionFactory;
	}
	
	public void setSessionFactory(DbSessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	@Override
	public CareModel saveCareModel(CareModel careModel) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(careModel);
		return careModel;
	}
	
	@Override
	public CareModel getCareModelById(Integer id) {
		return (CareModel) sessionFactory.getCurrentSession().get(CareModel.class, id);
	}
	
	@Override
	public CareModel getCareModelByIdForUpdate(Integer id) {
		return (CareModel) sessionFactory.getCurrentSession().createCriteria(CareModel.class).add(Restrictions.idEq(id))
		        .setLockMode(LockMode.PESSIMISTIC_WRITE).uniqueResult();
	}
	
	@Override
	public CareModel getCareModelByUuid(String uuid) {
		return (CareModel) sessionFactory.getCurrentSession().createCriteria(CareModel.class)
		        .add(Restrictions.eq("uuid", uuid)).uniqueResult();
	}
	
	@Override
	public CareModel getCareModelByCode(String code) {
		return (CareModel) sessionFactory.getCurrentSession().createCriteria(CareModel.class)
		        .add(Restrictions.eq("code", code)).uniqueResult();
	}
	
	@Override
	public CareModel getCareModelByCodeForUpdate(String code) {
		return (CareModel) sessionFactory.getCurrentSession().createCriteria(CareModel.class)
		        .add(Restrictions.eq("code", code)).setLockMode(LockMode.PESSIMISTIC_WRITE).uniqueResult();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<CareModel> getCareModels(String qStr, CareModelStatus status, boolean includeRetired, Integer startIndex,
	        Integer limit) {
		Criteria criteria = buildSearchCriteria(qStr, status, includeRetired);
		if (startIndex != null) {
			criteria.setFirstResult(startIndex);
		}
		if (limit != null) {
			criteria.setMaxResults(limit);
		}
		criteria.addOrder(Order.asc("name"));
		return criteria.list();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<CareModel> getPublishedCareModelsForEnrollment() {
		return sessionFactory.getCurrentSession().createCriteria(CareModel.class)
		        .add(Restrictions.eq("status", CareModelStatus.PUBLISHED)).add(Restrictions.eq("retired", false))
		        .addOrder(Order.asc("careModelId")).list();
	}
	
	@Override
	public long getCareModelsCount(String qStr, CareModelStatus status, boolean includeRetired) {
		Criteria criteria = buildSearchCriteria(qStr, status, includeRetired);
		criteria.setProjection(Projections.rowCount());
		return (Long) criteria.uniqueResult();
	}
	
	private Criteria buildSearchCriteria(String qStr, CareModelStatus status, boolean includeRetired) {
		Criteria criteria = sessionFactory.getCurrentSession().createCriteria(CareModel.class);
		if (!includeRetired) {
			criteria.add(Restrictions.eq("retired", false));
		}
		if (status != null) {
			criteria.add(Restrictions.eq("status", status));
		}
		if (qStr != null && !qStr.trim().isEmpty()) {
			String q = qStr.trim();
			criteria.add(Restrictions.or(Restrictions.ilike("name", q, MatchMode.ANYWHERE),
			    Restrictions.ilike("code", q, MatchMode.ANYWHERE)));
		}
		return criteria;
	}
	
	@Override
	public void purgeCareModel(CareModel careModel) throws DAOException {
		sessionFactory.getCurrentSession().delete(careModel);
	}
	
	@Override
	public CareModelRef getModelRef(Integer careModelId) {
		List<CareModelRef> refs = getModelRefs(java.util.Collections.singletonList(careModelId));
		return refs.isEmpty() ? null : refs.get(0);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<CareModelRef> getModelRefs(Collection<Integer> careModelIds) {
		if (careModelIds == null || careModelIds.isEmpty()) {
			return Collections.emptyList();
		}
		Criteria criteria = sessionFactory.getCurrentSession().createCriteria(CareModel.class);
		criteria.add(Restrictions.in("careModelId", careModelIds));
		criteria.setProjection(Projections.projectionList().add(Projections.property("careModelId"))
		        .add(Projections.property("uuid")).add(Projections.property("code")).add(Projections.property("name"))
		        .add(Projections.property("privilege")).add(Projections.property("status"))
		        .add(Projections.property("retired")));
		List<Object[]> rows = criteria.list();
		List<CareModelRef> refs = new java.util.ArrayList<CareModelRef>(rows.size());
		for (Object[] row : rows) {
			CareModelRef ref = new CareModelRef();
			ref.setCareModelId((Integer) row[0]);
			ref.setUuid((String) row[1]);
			ref.setCode((String) row[2]);
			ref.setName((String) row[3]);
			ref.setPrivilege((String) row[4]);
			// status projects as the CareModelStatus enum; normalize to its name
			ref.setStatus(row[5] instanceof CareModel.CareModelStatus ? ((CareModel.CareModelStatus) row[5]).name()
			        : (String) row[5]);
			ref.setRetired((Boolean) row[6]);
			refs.add(ref);
		}
		return refs;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<CareModel> getEnrollmentModelsAfter(int modelId, int limit) {
		if (limit < 1 || limit > 100)
			throw new IllegalArgumentException("Invalid model batch limit");
		return sessionFactory.getCurrentSession().createCriteria(CareModel.class)
		        .add(Restrictions.gt("careModelId", modelId)).add(Restrictions.eq("status", CareModelStatus.PUBLISHED))
		        .add(Restrictions.eq("retired", false)).addOrder(Order.asc("careModelId")).setMaxResults(limit).list();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<Integer> getPatientIdsAfter(int patientId, int upperId, int limit) {
		if (limit < 1 || limit > 100)
			throw new IllegalArgumentException("Invalid patient batch limit");
		return sessionFactory.getCurrentSession().createCriteria(org.openmrs.Patient.class)
		        .add(Restrictions.gt("patientId", patientId)).add(Restrictions.le("patientId", upperId))
		        .add(Restrictions.eq("voided", false)).setProjection(Projections.property("patientId"))
		        .addOrder(Order.asc("patientId")).setMaxResults(limit).list();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<Integer> getActivePatientIdsAfter(int patientId, int upperId, java.util.Date activeSince, int limit) {
		if (limit < 1 || limit > 100)
			throw new IllegalArgumentException("Invalid patient batch limit");
		List<List<Integer>> streams = new java.util.ArrayList<List<Integer>>(3);
		streams.add(activePatientIds(org.openmrs.Encounter.class, "patient.id", "encounterDatetime", patientId, upperId,
		    activeSince, limit));
		streams.add(activePatientIds(org.openmrs.Obs.class, "personId", "obsDatetime", patientId, upperId, activeSince,
		    limit));
		streams.add(activePatientIds(org.openmrs.PatientProgram.class, "patient.id", "dateEnrolled", patientId, upperId,
		    activeSince, limit));
		// k-way merge: each stream is ascending, so consuming the global minimum keeps the merged
		// output strictly ascending (the reconciliation cursor requires monotonic ids)
		int[] position = new int[streams.size()];
		List<Integer> merged = new java.util.ArrayList<Integer>(limit);
		while (merged.size() < limit) {
			Integer minimum = null;
			for (int s = 0; s < streams.size(); s++) {
				List<Integer> stream = streams.get(s);
				if (position[s] < stream.size()) {
					Integer candidate = stream.get(position[s]);
					if (minimum == null || candidate < minimum) {
						minimum = candidate;
					}
				}
			}
			if (minimum == null) {
				break;
			}
			for (int s = 0; s < streams.size(); s++) {
				List<Integer> stream = streams.get(s);
				while (position[s] < stream.size() && minimum.equals(stream.get(position[s]))) {
					position[s]++;
				}
			}
			merged.add(minimum);
		}
		return merged;
	}
	
	@SuppressWarnings("unchecked")
	private List<Integer> activePatientIds(Class<?> entity, String idProperty, String activityProperty, int patientId,
	        int upperId, java.util.Date activeSince, int limit) {
		return sessionFactory.getCurrentSession().createCriteria(entity).add(Restrictions.gt(idProperty, patientId))
		        .add(Restrictions.le(idProperty, upperId)).add(Restrictions.ge(activityProperty, activeSince))
		        .setProjection(Projections.distinct(Projections.property(idProperty))).addOrder(Order.asc(idProperty))
		        .setMaxResults(limit).list();
	}
	
	@Override
	public int getMaximumPatientId() {
		Integer maximum = (Integer) sessionFactory.getCurrentSession().createCriteria(org.openmrs.Patient.class)
		        .setProjection(Projections.max("patientId")).uniqueResult();
		return maximum == null ? 0 : maximum;
	}
	
	@Override
	public org.openmrs.GlobalProperty getReconciliationCursorForUpdate() {
		return (org.openmrs.GlobalProperty) sessionFactory.getCurrentSession()
		        .createCriteria(org.openmrs.GlobalProperty.class)
		        .add(Restrictions.eq("property", "caremodels.reconciliation.cursor"))
		        .setLockMode(LockMode.PESSIMISTIC_WRITE).uniqueResult();
	}
}
