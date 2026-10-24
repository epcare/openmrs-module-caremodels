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

import java.time.Clock;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openmrs.Concept;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptName;
import org.openmrs.ConceptNumeric;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;

/**
 * Grouped observations resolver (source type {@code observation-set}). Returns the patient's actual
 * recorded parent observation groups for a concept set — never a synthetic group assembled from the
 * most recent value of each member. Latest semantics are the newest qualifying parent group;
 * historical semantics are the distinct parent groups ordered by effective datetime with a
 * deterministic uuid tie-break, each preserving its child observations, their own datetimes and
 * repeated member measurements.
 * <p>
 * Voided parents and voided children are excluded. Only set membership ({@link Concept#isSet()}
 * plus actual recorded groups) qualifies a concept; an unsupported N/A concept that is not a set
 * fails closed. Qualifiers (value modifiers) are preserved verbatim; units and reference ranges are
 * surfaced only from the authoritative concept definition and never invented.
 */
public class ObservationSetResolver implements DataResolver {
	
	/** Depth cap for nested observation groups; deeper nesting is not serialized. */
	static final int MAX_GROUP_DEPTH = 3;
	
	public static final String ID = "observation-set";
	
	private final Clock clock;
	
	public ObservationSetResolver() {
		this(Clock.systemUTC());
	}
	
	public ObservationSetResolver(Clock clock) {
		this.clock = clock;
	}
	
	@Override
	public String getId() {
		return ID;
	}
	
	@Override
	public JsonNode resolve(Integer patientId, JsonNode source, String reference) {
		Patient patient = ResolverSupport.patient(patientId);
		if (reference == null)
			throw new IllegalArgumentException("A declared concept alias is required");
		Concept set = Context.getConceptService().getConceptByUuid(reference);
		if (set == null || set.isRetired())
			throw new IllegalArgumentException("Concept unavailable");
		if (!Boolean.TRUE.equals(set.isSet()))
			throw new IllegalArgumentException("Parent concept must be a concept set (isSet)");
		if (set.getSetMembers() == null || set.getSetMembers().isEmpty())
			throw new IllegalArgumentException("Concept set has no members");

		Instant now = clock.instant();
		int limit = ResolverSupport.limit(source, 50);
		Date since = ResolverSupport.since(source, now, 730);

		// Parent groups are the recorded observations of the set concept itself.
		List<Obs> parents = Context.getObsService().getObservations(Collections.singletonList(patient), null,
				Collections.singletonList(set), null, null, null, Arrays.asList("obsDatetime desc", "uuid asc"),
				limit + 1, null, since, Date.from(now), false);
		List<Obs> safe = new ArrayList<Obs>();
		for (Obs obs : parents) {
			if (!Boolean.TRUE.equals(obs.getVoided()) && patient.getId().equals(obs.getPersonId())
					&& set.equals(obs.getConcept()) && ResolverSupport.inWindow(obs.getObsDatetime(), since, now))
				safe.add(obs);
		}
		safe.sort(ResolverSupport.newest(Obs::getObsDatetime, Obs::getUuid));

		Map<String, Integer> displayOrder = displayOrder(set);
		// Optional declared member filter: only listed concept uuids are serialized.
		Set<String> memberFilter = null;
		if (source.hasNonNull("members") && source.path("members").isArray() && source.path("members").size() > 0) {
			memberFilter = new HashSet<String>();
			for (JsonNode member : source.path("members")) memberFilter.add(member.asText());
		}
		ObjectNode result = ResolverSupport.result(ID, now).put("setConceptUuid", reference)
				.put("setDisplay", display(set));
		ArrayNode groups = result.putArray("groups");
		for (Obs parent : safe.subList(0, Math.min(limit, safe.size())))
			groups.add(group(parent, displayOrder, 0, memberFilter));
		ResolverSupport.collection(result, groups, safe.size(), limit);
		return result;
	}
	
	/** Set-member display order by uuid, following the concept set's own sort weight. */
	private Map<String, Integer> displayOrder(Concept set) {
		Map<String, Integer> order = new HashMap<String, Integer>();
		List<Concept> members = set.getSetMembers();
		if (members != null)
			for (int i = 0; i < members.size(); i++)
				order.put(members.get(i).getUuid(), i);
		return order;
	}
	
	/**
	 * Context-free display resolution from the concept's own names, preferring the fully specified
	 * name. Unlike {@link Concept#getName()} this never depends on the thread's locale context; the
	 * frontend still prefers widget-configured member labels.
	 */
	static String display(Concept concept) {
		if (concept == null)
			return null;
		Collection<ConceptName> names = concept.getNames();
		if (names == null || names.isEmpty())
			return null;
		String first = null;
		for (ConceptName name : names) {
			if (name == null || Boolean.TRUE.equals(name.getVoided()))
				continue;
			if (org.openmrs.api.ConceptNameType.FULLY_SPECIFIED.equals(name.getConceptNameType()))
				return name.getName();
			if (first == null)
				first = name.getName();
		}
		return first;
	}
	
	private ObjectNode group(Obs parent, Map<String, Integer> displayOrder, int depth, Set<String> memberFilter) {
		ObjectNode group = ResolverSupport.MAPPER.createObjectNode().put("parentObsUuid", parent.getUuid())
				.put("effectiveDate", ResolverSupport.time(parent.getObsDatetime()));
		if (parent.getEncounter() != null && parent.getEncounter().getUuid() != null)
			group.put("encounterUuid", parent.getEncounter().getUuid());
		else
			group.putNull("encounterUuid");
		ArrayNode members = group.putArray("members");
		List<Obs> children = new ArrayList<Obs>();
		if (parent.getGroupMembers() != null)
			for (Obs child : parent.getGroupMembers())
				if (!Boolean.TRUE.equals(child.getVoided())
				        && (memberFilter == null || (child.getConcept() != null && memberFilter.contains(child.getConcept().getUuid()))))
					children.add(child);
		final Map<String, String> displays = new HashMap<String, String>();
		for (Obs child : children)
			if (child.getConcept() != null)
				displays.put(child.getUuid(), display(child.getConcept()));
		// Set display order first (concept sort weight), then recorded time, then uuid: repeated
		// measurements of one member keep their individual datetimes and are never overwritten.
		children.sort(Comparator
				.comparing((Obs obs) -> obs.getConcept() == null ? Integer.MAX_VALUE
						: displayOrder.getOrDefault(obs.getConcept().getUuid(), Integer.MAX_VALUE))
				.thenComparing(obs -> displays.getOrDefault(obs.getUuid(), ""))
				.thenComparing(ResolverSupport.newest(Obs::getObsDatetime, Obs::getUuid)));
		for (Obs child : children)
			members.add(member(child, displayOrder, depth, memberFilter));
		return group;
	}
	
	private ObjectNode member(Obs obs, Map<String, Integer> displayOrder, int depth, Set<String> memberFilter) {
		Concept concept = obs.getConcept();
		ObjectNode member = ResolverSupport.MAPPER.createObjectNode().put("obsUuid", obs.getUuid())
		        .put("conceptUuid", concept == null ? null : concept.getUuid()).put("conceptDisplay", display(concept));
		if (obs.getObsDatetime() != null)
			member.put("effectiveTime", ResolverSupport.time(obs.getObsDatetime()));
		if (obs.getComment() != null && !obs.getComment().trim().isEmpty())
			member.put("comment", obs.getComment());
		String datatype = datatype(concept);
		member.put("datatype", datatype);
		switch (datatype) {
			case "NUMERIC":
				if (obs.getValueNumeric() != null && Double.isFinite(obs.getValueNumeric()))
					member.put("value", obs.getValueNumeric());
				else
					member.putNull("value");
				if (concept instanceof ConceptNumeric && ((ConceptNumeric) concept).getUnits() != null)
					member.put("unit", ((ConceptNumeric) concept).getUnits());
				if (concept instanceof ConceptNumeric)
					referenceRange(member, (ConceptNumeric) concept);
				break;
			case "CODED":
				if (obs.getValueCoded() != null) {
					ObjectNode coded = member.putObject("value");
					coded.put("conceptUuid", obs.getValueCoded().getUuid());
					coded.put("display", display(obs.getValueCoded()));
				} else
					member.putNull("value");
				break;
			case "TEXT":
				if (obs.getValueText() != null)
					member.put("value", obs.getValueText());
				else
					member.putNull("value");
				break;
			case "DATE":
			case "DATETIME":
				member.put("value", obs.getValueDatetime() == null ? null : ResolverSupport.time(obs.getValueDatetime()));
				break;
			case "BOOLEAN":
				if (obs.getValueBoolean() != null)
					member.put("value", obs.getValueBoolean().booleanValue());
				else
					member.putNull("value");
				break;
			default:
				member.putNull("value");
		}
		// Qualifiers such as < or > are recorded data, preserved verbatim.
		String modifier = obs.getValueModifier();
		if (modifier != null && !modifier.trim().isEmpty() && !"=".equals(modifier))
			member.put("qualifier", modifier);
		// Nested observation relationships are preserved up to the depth cap.
		Collection<Obs> nested = obs.getGroupMembers();
		boolean hasNested = false;
		for (Obs child : nested == null ? Collections.<Obs> emptyList() : nested)
			hasNested |= !Boolean.TRUE.equals(child.getVoided());
		if (hasNested && depth + 1 < MAX_GROUP_DEPTH)
			member.set("members", group(obs, displayOrder, depth + 1, memberFilter).get("members"));
		return member;
	}
	
	private void referenceRange(ObjectNode member, ConceptNumeric concept) {
		Double low = concept.getLowNormal();
		Double high = concept.getHiNormal();
		if (low == null && high == null)
			return;
		ObjectNode range = member.putObject("referenceRange");
		if (low != null)
			range.put("low", low);
		else
			range.putNull("low");
		if (high != null)
			range.put("high", high);
		else
			range.putNull("high");
	}
	
	/** Wire datatype name; unsupported or N/A datatypes travel as UNKNOWN with a null value. */
	static String datatype(Concept concept) {
		if (concept == null || concept.getDatatype() == null)
			return "UNKNOWN";
		ConceptDatatype datatype = concept.getDatatype();
		String name = datatype.getName() == null ? "" : datatype.getName().toLowerCase(Locale.ENGLISH);
		// datatype ids are metadata-table specific; the name is the stable contract
		if (datatype.isNumeric() || name.equals("numeric") || name.equals("structured numeric"))
			return "NUMERIC";
		if (datatype.isCoded() || name.equals("coded"))
			return "CODED";
		if (datatype.isText() || name.equals("text"))
			return "TEXT";
		if (datatype.isDateTime() || name.equals("datetime"))
			return "DATETIME";
		if (datatype.isDate() || name.equals("date"))
			return "DATE";
		if (datatype.isBoolean() || name.equals("boolean"))
			return "BOOLEAN";
		return "UNKNOWN";
	}
}
