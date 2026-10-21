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
import org.openmrs.parameter.OrderSearchCriteriaBuilder;

/** Recorded order history for exactly one declared concept. Not a complete current regimen. */
public class OrderResolver implements DataResolver {

	@Override
	public String getId() {
		return "order-query";
	}

	@Override public JsonNode resolve(Integer patientId, JsonNode source, String reference) {
        Patient patient = ResolverSupport.patient(patientId);
        Concept concept = reference == null ? null : Context.getConceptService().getConceptByUuid(reference);
        if (concept == null || concept.isRetired()) throw new IllegalArgumentException("Order concept unavailable");
        Instant now = Instant.now();
        int limit = ResolverSupport.limit(source, 100);
        Date since = ResolverSupport.since(source, now, 365);
        List<Order> safe = new ArrayList<>();
        for (Order order : Context.getOrderService().getOrders(new OrderSearchCriteriaBuilder().setPatient(patient)
                .setConcepts(Collections.singletonList(concept)).setActivatedOnOrAfterDate(since)
                .setActivatedOnOrBeforeDate(Date.from(now)).setIncludeVoided(false).build())) {
            if (!Boolean.TRUE.equals(order.getVoided()) && patient.equals(order.getPatient()) && concept.equals(order.getConcept())
                && ResolverSupport.inWindow(order.getDateActivated(), since, now)) safe.add(order);
        }
        safe.sort(ResolverSupport.newest(Order::getDateActivated, Order::getUuid));
        ObjectNode result = ResolverSupport.result(getId(), now).put("concept", reference).put("scope", "concept-order-history");
        result.putArray("availableFields").add("uuid").add("action").add("dateActivated").add("effectiveStartDate").add("effectiveStopDate").add("autoExpireDate").add("activeAtAssessment").add("previousOrderUuid").add("orderTypeUuid").add("drugUuid").add("drugName").add("dose").add("doseUnitsConceptUuid");
        ArrayNode records = result.putArray("records");
        for (Order order : safe.subList(0, Math.min(limit, safe.size()))) {
            ObjectNode row = records.addObject().put("uuid", order.getUuid()).put("action", order.getAction() == null ? null : order.getAction().name())
                .put("dateActivated", ResolverSupport.time(order.getDateActivated())).put("effectiveStartDate", ResolverSupport.time(order.getEffectiveStartDate()))
                .put("effectiveStopDate", ResolverSupport.time(order.getEffectiveStopDate())).put("autoExpireDate", ResolverSupport.time(order.getAutoExpireDate()))
                .put("activeAtAssessment", order.isActive(Date.from(now)))
                .put("previousOrderUuid", order.getPreviousOrder() == null ? null : order.getPreviousOrder().getUuid())
                .put("orderTypeUuid", order.getOrderType() == null ? null : order.getOrderType().getUuid());
            if (order instanceof DrugOrder) {
                DrugOrder drugOrder = (DrugOrder) order;
                row.put("drugUuid", drugOrder.getDrug() == null ? null : drugOrder.getDrug().getUuid());
                row.put("drugName", drugOrder.getDrug() == null ? null : drugOrder.getDrug().getName());
                if (drugOrder.getDose() != null) row.put("dose", drugOrder.getDose());
                row.put("doseUnitsConceptUuid", drugOrder.getDoseUnits() == null ? null : drugOrder.getDoseUnits().getUuid());
            }
        }
        ResolverSupport.collection(result, records, safe.size(), limit);
        return result;
    }
}
