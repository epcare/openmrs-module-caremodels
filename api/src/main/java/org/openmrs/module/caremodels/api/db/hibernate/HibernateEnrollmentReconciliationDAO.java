/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.api.db.hibernate;

import java.util.List;

import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.openmrs.Patient;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.caremodels.api.db.EnrollmentReconciliationDAO;

public class HibernateEnrollmentReconciliationDAO implements EnrollmentReconciliationDAO {
	
	private DbSessionFactory sessionFactory;
	
	public void setSessionFactory(DbSessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<Integer> getPatientIdsAfter(Integer afterPatientId, int limit) {
		return sessionFactory.getCurrentSession().createCriteria(Patient.class)
		        .add(Restrictions.gt("patientId", afterPatientId == null ? 0 : afterPatientId))
		        .add(Restrictions.eq("voided", false)).addOrder(Order.asc("patientId"))
		        .setProjection(Projections.property("patientId")).setMaxResults(limit).list();
	}
}
