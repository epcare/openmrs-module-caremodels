package org.openmrs.module.caremodels.web.resource;

import java.util.ArrayList;
import java.util.List;

/** Request and structured response of POST /caremodels/enrollmentcheck. Nothing persists here. */
public class EnrollmentCheckRequest {

	private String patient;

	private String model;

	private String modelCode;

	private String status;

	private boolean canEnroll;

	private boolean requiresReason;

	private boolean requiresConfirmation;

	private List<String> reasons = new ArrayList<>();

	public String getPatient() {
		return patient;
	}

	public void setPatient(String patient) {
		this.patient = patient;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getModelCode() {
		return modelCode;
	}

	public void setModelCode(String modelCode) {
		this.modelCode = modelCode;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public boolean isCanEnroll() {
		return canEnroll;
	}

	public void setCanEnroll(boolean canEnroll) {
		this.canEnroll = canEnroll;
	}

	public boolean isRequiresReason() {
		return requiresReason;
	}

	public void setRequiresReason(boolean requiresReason) {
		this.requiresReason = requiresReason;
	}

	public boolean isRequiresConfirmation() {
		return requiresConfirmation;
	}

	public void setRequiresConfirmation(boolean requiresConfirmation) {
		this.requiresConfirmation = requiresConfirmation;
	}

	public List<String> getReasons() {
		return reasons;
	}

	public void setReasons(List<String> reasons) {
		this.reasons = reasons;
	}
}
