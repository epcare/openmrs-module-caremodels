/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.openmrs.module.caremodels.web.resource;

import static org.junit.Assert.assertEquals;

import java.util.Date;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openmrs.module.caremodels.api.CareModelReconciliationService;
import org.openmrs.module.caremodels.dto.EnrollmentReconciliationResult;

public class ReconciliationOperationResourceTest {
	
	private RecordingService service;
	
	private ReconciliationOperationResource resource;
	
	@BeforeClass
	public static void configureApplicationDataDirectory() {
		System.setProperty("OPENMRS_APPLICATION_DATA_DIRECTORY", System.getProperty("java.io.tmpdir"));
	}
	
	@Before
	public void setup() {
		service = new RecordingService();
		resource = new ReconciliationOperationResource() {
			
			@Override
			protected CareModelReconciliationService service() {
				return service;
			}
		};
	}
	
	@Test
	public void shouldDispatchBatchAndReturnContinuationData() {
		ReconciliationOperationRequest request = new ReconciliationOperationRequest();
		request.setAfterPatientId(40);
		request.setLimit(10);
		
		ReconciliationOperationRequest response = resource.save(request);
		
		assertEquals(Integer.valueOf(40), service.afterPatientId);
		assertEquals(Integer.valueOf(10), service.limit);
		assertEquals(Integer.valueOf(49), response.getNextAfterPatientId());
		assertEquals(Integer.valueOf(10), response.getProcessedPatients());
		assertEquals(Boolean.TRUE, response.getHasMore());
	}
	
	@Test
	public void postCreationMustBindCursorLimitAndEvaluationTime() {
		Date asOf = new Date(1761033600000L);
		org.openmrs.module.webservices.rest.SimpleObject request = new org.openmrs.module.webservices.rest.SimpleObject();
		request.add("afterPatientId", 40);
		request.add("limit", 10);
		request.add("asOf", asOf);
		try (org.mockito.MockedStatic<org.openmrs.api.context.Context> context =
                org.mockito.Mockito.mockStatic(org.openmrs.api.context.Context.class)) {
            resource.save(resource.convert(request));
        }
		assertEquals(Integer.valueOf(40), service.afterPatientId);
		assertEquals(Integer.valueOf(10), service.limit);
		assertEquals(asOf, service.asOf);
	}
	
	private static final class RecordingService implements CareModelReconciliationService {
		
		@Override
		public int reconcileNextBatch(int limit) {
			throw new UnsupportedOperationException();
		}
		
		private Integer afterPatientId;
		
		private Integer limit;
		
		private Date asOf;
		
		@Override
		public EnrollmentReconciliationResult reconcile(Integer cursor, Integer batchLimit, Date asOf) {
			this.asOf = asOf;
			afterPatientId = cursor;
			limit = batchLimit;
			EnrollmentReconciliationResult result = new EnrollmentReconciliationResult();
			result.setNextAfterPatientId(49);
			result.setProcessedPatients(10);
			result.setEvaluatedModels(20);
			result.setAppliedDecisions(2);
			result.setFailedEvaluations(0);
			result.setHasMore(true);
			return result;
		}
		
		@Override
		public void onStartup() {
		}
		
		@Override
		public void onShutdown() {
		}
	}
}
