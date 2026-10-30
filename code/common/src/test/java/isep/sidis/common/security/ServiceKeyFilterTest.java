package isep.sidis.common.security;

import isep.sidis.common.http.AisafeHeaders;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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

    private static MockHttpServletRequest peerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AisafeHeaders.PEER_HOPS, "1");
        return request;
    }

    private static FilterChain chain(AtomicBoolean called) {
        return (request, response) -> called.set(true);
    }
}
