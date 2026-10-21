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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of built-in and registered data resolvers. Definitions may only name resolvers present
 * here; unknown ids fail closed at the data endpoint, never at runtime discovery.
 */
public class DataResolverRegistry {
	
	private final Map<String, DataResolver> resolvers = new HashMap<String, DataResolver>();
	
	public DataResolverRegistry() {
		register(new LatestObservationResolver());
	}
	
	public synchronized void register(DataResolver resolver) {
		resolvers.put(resolver.getId(), resolver);
	}
	
	public synchronized DataResolver getResolver(String id) {
		return resolvers.get(id);
	}
	
	public synchronized List<String> getResolverIds() {
		return Collections.unmodifiableList(new ArrayList<String>(resolvers.keySet()));
	}
}
