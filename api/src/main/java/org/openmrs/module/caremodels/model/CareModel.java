/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.caremodels.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;

import org.openmrs.BaseOpenmrsMetadata;

/**
 * A locally-authored Care Model. The model identity (code, name, required view privilege) and
 * lifecycle status live in table columns; the versioned JSON envelope (editable draft, published
 * snapshot with checksum, bounded history) is stored opaquely in {@code configuration}.
 * <p>
 * The required view privilege is stored ONLY here, never inside the definition JSON. A blank,
 * unregistered or missing privilege fails closed: the model is not visible to anyone who does not
 * hold the named privilege.
 */
@Entity
@Table(name = "care_model", indexes = { @Index(name = "idx_care_model_uuid", columnList = "uuid"),
        @Index(name = "idx_care_model_code", columnList = "code"),
        @Index(name = "idx_care_model_privilege", columnList = "privilege"),
        @Index(name = "idx_care_model_status", columnList = "status"),
        @Index(name = "idx_care_model_retired", columnList = "retired") })
public class CareModel extends BaseOpenmrsMetadata implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "care_model_id")
	private Integer careModelId;
	
	/**
	 * Local unique stable business code; used by the frontend to route and to keep imports
	 * idempotent.
	 */
	@Column(name = "code", length = 100, nullable = false, unique = true)
	private String code;
	
	/** Required model-view privilege name; must reference an existing OpenMRS privilege. */
	@Column(name = "privilege", length = 255, nullable = false)
	private String privilege;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 20, nullable = false)
	private CareModelStatus status = CareModelStatus.DRAFT;
	
	/** Envelope revision for optimistic compare-and-swap writes of the draft/published JSON. */
	@Column(name = "revision", nullable = false)
	private Integer revision = 0;
	
	/** Versioned JSON storage envelope (storageSchemaVersion, revision, draft, published, history). */
	@Lob
	@Column(name = "configuration", columnDefinition = "LONGTEXT", nullable = false)
	private String configuration;
	
	// ========================
	// Enums
	// ========================
	
	/**
	 * Local model lifecycle. An unpublished (DRAFT) or inactive/retired model never appears on
	 * patient tabs.
	 */
	public enum CareModelStatus {
		DRAFT, PUBLISHED, INACTIVE, RETIRED
	}
	
	// ========================
	// ID mapping (OpenMRS)
	// ========================
	
	@Override
	public Integer getId() {
		return careModelId;
	}
	
	@Override
	public void setId(Integer id) {
		this.careModelId = id;
	}
	
	// ========================
	// Getters and setters
	// ========================
	
	public String getCode() {
		return code;
	}
	
	public void setCode(String code) {
		this.code = code;
	}
	
	public String getPrivilege() {
		return privilege;
	}
	
	public void setPrivilege(String privilege) {
		this.privilege = privilege;
	}
	
	public CareModelStatus getStatus() {
		return status;
	}
	
	public void setStatus(CareModelStatus status) {
		this.status = status;
	}
	
	public Integer getRevision() {
		return revision;
	}
	
	public void setRevision(Integer revision) {
		this.revision = revision;
	}
	
	public String getConfiguration() {
		return configuration;
	}
	
	public void setConfiguration(String configuration) {
		this.configuration = configuration;
	}
}
