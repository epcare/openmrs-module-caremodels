/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of structural and semantic validation: an ordered list of errors with JSON paths.
 * Presentation-friendly: callers surface these directly to the designer UI, not raw stack traces.
 */
public class ValidationResult {
	
	private final List<Error> errors = new ArrayList<Error>();
	
	public static class Error {
		
		private final String path;
		
		private final String message;
		
		public Error(String path, String message) {
			this.path = path;
			this.message = message;
		}
		
		public String getPath() {
			return path;
		}
		
		public String getMessage() {
			return message;
		}
		
		@Override
		public String toString() {
			return path + ": " + message;
		}
	}
	
	public static ValidationResult valid() {
		return new ValidationResult();
	}
	
	public static ValidationResult withError(String path, String message) {
		ValidationResult result = new ValidationResult();
		result.addError(path, message);
		return result;
	}
	
	public void addError(String path, String message) {
		errors.add(new Error(path, message));
	}
	
	public void merge(ValidationResult other) {
		if (other != null) {
			errors.addAll(other.errors);
		}
	}
	
	public boolean isValid() {
		return errors.isEmpty();
	}
	
	public List<Error> getErrors() {
		return Collections.unmodifiableList(errors);
	}
	
	@Override
	public String toString() {
		if (errors.isEmpty()) {
			return "valid";
		}
		StringBuilder sb = new StringBuilder(errors.size() + " validation error(s): ");
		for (Error error : errors) {
			sb.append("\n  - ").append(error);
		}
		return sb.toString();
	}
}
