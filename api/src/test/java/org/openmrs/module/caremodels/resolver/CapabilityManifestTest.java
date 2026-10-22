package org.openmrs.module.caremodels.resolver;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CapabilityManifestTest {
	
	@Test public void releaseManifestMustListOnlyExecutableResolversAndExactly25CatalogEntries() throws Exception {
        JsonNode manifest = new ObjectMapper().readTree(getClass().getResourceAsStream("/widget-capabilities.json"));
        Set<String> resolvers = new HashSet<>();
        for (JsonNode id : manifest.path("registeredResolvers")) resolvers.add(id.asText());
        assertEquals(new HashSet<>(new DataResolverRegistry().getResolverIds()), resolvers);
        assertEquals(25, manifest.path("widgets").size());
        for (JsonNode widget : manifest.path("widgets")) {
            assertFalse(widget.path("frontendIntegrationVerified").asBoolean());
            assertFalse(widget.path("liveHttpVerified").asBoolean());
        }
    }
}
