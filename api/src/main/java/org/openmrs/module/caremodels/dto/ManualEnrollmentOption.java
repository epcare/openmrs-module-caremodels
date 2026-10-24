package org.openmrs.module.caremodels.dto;

/**
 * Read-only manual-enrollment discovery result for one published model. Eligibility is derived from
 * the model's own enrollment policy only; clinical observation data never participates.
 */
public class ManualEnrollmentOption {
	
	private String modelUuid;
	
	private String modelCode;
	
	private String displayLabel;
	
	private String category;
	
	private boolean requiresReason;
	
	private boolean requiresConfirmation;
	
	private boolean alreadyEnrolled;
	
	private String openStatus;
	
	private boolean eligible;
	
	private String reason;
	
	public String getModelUuid() {
		return modelUuid;
	}
	
	public void setModelUuid(String modelUuid) {
		this.modelUuid = modelUuid;
	}
	
	public String getModelCode() {
		return modelCode;
	}
	
	public void setModelCode(String modelCode) {
		this.modelCode = modelCode;
	}
	
	public String getDisplayLabel() {
		return displayLabel;
	}
	
	public void setDisplayLabel(String displayLabel) {
		this.displayLabel = displayLabel;
	}
	
	public String getCategory() {
		return category;
	}
	
	public void setCategory(String category) {
		this.category = category;
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
	
	public boolean isAlreadyEnrolled() {
		return alreadyEnrolled;
	}
	
	public void setAlreadyEnrolled(boolean alreadyEnrolled) {
		this.alreadyEnrolled = alreadyEnrolled;
	}
	
	public String getOpenStatus() {
		return openStatus;
	}
	
	public void setOpenStatus(String openStatus) {
		this.openStatus = openStatus;
	}
	
	public boolean isEligible() {
		return eligible;
	}
	
	public void setEligible(boolean eligible) {
		this.eligible = eligible;
	}
	
	public String getReason() {
		return reason;
	}
	
	public void setReason(String reason) {
		this.reason = reason;
	}
}
