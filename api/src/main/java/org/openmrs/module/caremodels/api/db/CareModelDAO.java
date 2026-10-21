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

import java.util.Collection;
import java.util.List;

import org.openmrs.module.caremodels.dto.CareModelRef;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.caremodels.model.CareModel.CareModelStatus;

/**
 * Database access for {@link CareModel}. Queries for the membership listing deliberately do not
 * select {@code configuration}; the JSON envelope is only loaded on explicit model/workspace
 * requests.
 */
public interface CareModelDAO {
	
	CareModel saveCareModel(CareModel careModel);
	
	CareModel getCareModelById(Integer id);

	/** Locks one model row to serialize membership episode creation for that model. */
	CareModel getCareModelByIdForUpdate(Integer id);
	
	CareModel getCareModelByUuid(String uuid);
	
	CareModel getCareModelByCode(String code);
	
	/**
	 * Loads a model while holding a database write lock for the remainder of the current
	 * transaction. Draft and publish operations use this method so the envelope revision check and
	 * subsequent write form one atomic compare-and-swap operation.
	 */
	CareModel getCareModelByCodeForUpdate(String code);

	/**
	 * Search/filter the model registry. Excludes the {@code configuration} envelope.
	 * 
	 * @param qStr free-text match on name/code
	 * @param status optional status filter
	 * @param includeRetired whether retired models are included
	 */
	List<CareModel> getCareModels(String qStr, CareModelStatus status, boolean includeRetired, Integer startIndex,
	        Integer limit);

	/** Published, non-retired models whose definitions may react to a clinical change. */
	List<CareModel> getPublishedCareModelsForEnrollment();
	
	long getCareModelsCount(String qStr, CareModelStatus status, boolean includeRetired);
	
	void purgeCareModel(CareModel careModel);
	
	/**
	 * Metadata projection for the given model, selected without the {@code configuration} column.
	 * The membership listing path uses this exclusively so the JSON envelope is never fetched.
	 * 
	 * @return the projection, or null when no model exists for the id
	 */
	CareModelRef getModelRef(Integer careModelId);
	
	/**
	 * Metadata projections for many models in one query; {@code configuration} is never selected.
	 */
	List<CareModelRef> getModelRefs(Collection<Integer> careModelIds);
}
