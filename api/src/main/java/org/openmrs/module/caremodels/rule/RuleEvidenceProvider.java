/** This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. */
package org.openmrs.module.caremodels.rule;

/** Supplies typed facts to the rule evaluator without coupling it to OpenMRS service lookups. */
public interface RuleEvidenceProvider {

	RuleEvidence resolve(String type, String alias);
}
