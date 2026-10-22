package org.openmrs.module.caremodels.web.resource;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.*;
import org.mockito.MockedStatic;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.caremodels.api.*;
import org.openmrs.module.caremodels.dto.CareModelMembershipDTO;
import org.openmrs.module.caremodels.model.CareModel;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.resource.impl.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class FilteredReadResourceTest {

	private MockedStatic<Context> context;

	private CareModelAccessService access;

	@Before public void setup() {
        context = mockStatic(Context.class);
        access = mock(CareModelAccessService.class);
        context.when(() -> Context.getService(CareModelAccessService.class)).thenReturn(access);
    }

	@After
	public void cleanup() {
		if (context != null)
			context.close();
	}

	private RequestContext request() {
		RequestContext request = mock(RequestContext.class);
		when(request.getStartIndex()).thenReturn(0);
		when(request.getLimit()).thenReturn(10);
		return request;
	}

	@Test public void filteredMembershipSearchMustCallTheAuthorizedMetadataService() {
        RequestContext request = request(); when(request.getParameter("patient")).thenReturn("patient");
        CareModelMembershipDTO member = new CareModelMembershipDTO(); member.setMemberUuid("member");
        when(access.getAuthorizedMemberships("patient")).thenReturn(Collections.singletonList(member));
        NeedsPaging<?> result = (NeedsPaging<?>)new MembershipResource().doSearch(request);
        assertSame(member, result.getPageOfResults().get(0));
        verify(access).getAuthorizedMemberships("patient");
        context.verify(Context::getObsService, never());
    }

	@Test public void filteredWorkspaceSearchMustPreserveSelectedTabAndDescriptors() throws Exception {
        RequestContext request = request(); when(request.getParameter("patient")).thenReturn("patient");
        when(request.getParameter("model")).thenReturn("model"); when(request.getParameter("tab")).thenReturn("overview");
        ObjectMapper mapper = new ObjectMapper(); JsonNode definition = mapper.readTree("{\"forms\":[],\"actions\":[]}");
        JsonNode tab = mapper.readTree("{\"id\":\"overview\",\"label\":\"Overview\",\"widgets\":[],\"layouts\":{\"wide\":[]}}");
        when(access.authorizeWorkspace("patient", "model", "overview")).thenReturn(new CareModelAccessService.AuthorizedAccess(
            "patient", "model", "model-uuid", "member", definition, tab, mapper.createArrayNode()));
        NeedsPaging<?> result = (NeedsPaging<?>)new WorkspaceResource().doSearch(request);
        WorkspaceTab workspace = (WorkspaceTab)result.getPageOfResults().get(0);
        assertEquals("overview", workspace.getTabId()); assertNotNull(workspace.getDescriptors());
        assertTrue(workspace.getLayouts().containsKey("wide"));
        verify(access).authorizeWorkspace("patient", "model", "overview");
        context.verify(Context::getObsService, never());
    }

	@Test public void registrySearchMustNotDoublePageAnAlreadyPagedCollection() {
        RequestContext request = request(); when(request.getStartIndex()).thenReturn(10);
        when(request.getParameter("q")).thenReturn(" HIV "); when(request.getParameter("status")).thenReturn("PUBLISHED");
        CareModelService models = mock(CareModelService.class);
        context.when(() -> Context.getService(CareModelService.class)).thenReturn(models);
        CareModel model = new CareModel();
        when(models.getCareModels("HIV", CareModel.CareModelStatus.PUBLISHED, false, 10, 10)).thenReturn(Collections.singletonList(model));
        when(models.getCareModelsCount("HIV", CareModel.CareModelStatus.PUBLISHED, false)).thenReturn(21L);
        AlreadyPaged<?> result = (AlreadyPaged<?>)new ModelResource().doSearch(request);
        assertSame(model, result.getPageOfResults().get(0)); assertTrue(result.hasMoreResults()); assertEquals(Long.valueOf(21), result.getTotalCount());
    }

	@Test(expected = APIException.class)
	public void filteredWorkspaceSearchMustNotBypassDenials() {
		RequestContext request = request();
		when(request.getParameter("patient")).thenReturn("patient");
		when(request.getParameter("model")).thenReturn("model");
		when(access.authorizeWorkspace("patient", "model", null)).thenThrow(new APIException("denied"));
		new WorkspaceResource().doSearch(request);
	}

	@Test public void metadataUpdatesMustNotWriteLifecycleOrEnvelopeFields() {
        assertEquals(new HashSet<>(Arrays.asList("name", "description", "privilege")), new ModelResource().getUpdatableProperties().getProperties().keySet());
    }
}
