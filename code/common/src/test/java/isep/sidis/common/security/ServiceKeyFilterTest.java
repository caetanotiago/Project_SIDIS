package isep.sidis.common.security;

import isep.sidis.common.http.AisafeHeaders;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests peer request authentication using the shared service key.
 *
 * @author Diogo Fernandes - 1240632
 */
class ServiceKeyFilterTest {

    private final ServiceKeyFilter filter = new ServiceKeyFilter("shared-secret");

    @Test
    void rejectsPeerRequestWithoutServiceKey() throws Exception {
        MockHttpServletRequest request = peerRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilterInternal(request, response, chain(chainCalled));

        assertEquals(401, response.getStatus());
        assertFalse(chainCalled.get());
    }

    @Test
    void rejectsPeerRequestWithInvalidServiceKey() throws Exception {
        MockHttpServletRequest request = peerRequest();
        request.addHeader(AisafeHeaders.SERVICE_KEY, "invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilterInternal(request, response, chain(chainCalled));

        assertEquals(401, response.getStatus());
        assertFalse(chainCalled.get());
    }

    @Test
    void permitsPeerRequestWithValidServiceKey() throws Exception {
        MockHttpServletRequest request = peerRequest();
        request.addHeader(AisafeHeaders.SERVICE_KEY, "shared-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilterInternal(request, response, chain(chainCalled));

        assertTrue(chainCalled.get());
    }

    // ---- caminhos só para serviços (aisafe.security.service-only-paths)

    private final ServiceKeyFilter serviceOnlyFilter = new ServiceKeyFilter("shared-secret",
            List.of("/api/routes/statistics/**", " /api/routes/compatible "));

    @Test
    void rejectsServiceOnlyPathWithoutServiceKey() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        serviceOnlyFilter.doFilterInternal(get("/api/routes/statistics/count-by-airport"), response, chain(chainCalled));

        assertEquals(401, response.getStatus());
        assertEquals("{\"error\":\"This endpoint is only available to AISafe services.\"}", response.getContentAsString());
        assertFalse(chainCalled.get());
    }

    @Test
    void rejectsServiceOnlyPathWithInvalidServiceKey() throws Exception {
        MockHttpServletRequest request = get("/api/routes/compatible");
        request.addHeader(AisafeHeaders.SERVICE_KEY, "invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        serviceOnlyFilter.doFilterInternal(request, response, chain(chainCalled));

        assertEquals(401, response.getStatus());
        assertFalse(chainCalled.get());
    }

    @Test
    void passesServiceOnlyPathWithValidServiceKeyToTheNextFilters() throws Exception {
        MockHttpServletRequest request = get("/api/routes/compatible");
        request.addHeader(AisafeHeaders.SERVICE_KEY, "shared-secret");
        AtomicBoolean chainCalled = new AtomicBoolean();

        serviceOnlyFilter.doFilterInternal(request, new MockHttpServletResponse(), chain(chainCalled));

        assertTrue(chainCalled.get()); // o JWT e os roles são verificados a seguir
    }

    @Test
    void encodedVariantOfAServiceOnlyPathIsAlsoRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        serviceOnlyFilter.doFilterInternal(get("/api/routes/%63ompatible"), response, chain(new AtomicBoolean()));

        assertEquals(401, response.getStatus());
    }

    @Test
    void otherPathsDoNotNeedTheServiceKey() throws Exception {
        AtomicBoolean chainCalled = new AtomicBoolean();

        serviceOnlyFilter.doFilterInternal(get("/api/routes/search"), new MockHttpServletResponse(), chain(chainCalled));

        assertTrue(chainCalled.get());
    }

    @Test
    void contextPathIsIgnoredWhenMatching() throws Exception {
        MockHttpServletRequest request = get("/app/api/routes/compatible");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();

        serviceOnlyFilter.doFilterInternal(request, response, chain(new AtomicBoolean()));

        assertEquals(401, response.getStatus());
    }

    private static MockHttpServletRequest get(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private static MockHttpServletRequest peerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AisafeHeaders.PEER_HOPS, "1");
        return request;
    }

    private static FilterChain chain(AtomicBoolean called) {
        return (request, response) -> called.set(true);
    }
}
