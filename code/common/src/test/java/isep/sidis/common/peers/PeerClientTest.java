package isep.sidis.common.peers;

import com.sun.net.httpserver.HttpServer;
import isep.sidis.common.http.AisafeHeaders;
import isep.sidis.common.http.OutboundHeaders;
import isep.sidis.common.resilience.CircuitBreakerRegistry;
import isep.sidis.common.resilience.ResilientExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa o encaminhamento peer-to-peer contra servidores HTTP reais (réplicas falsas).
 */
class PeerClientTest {

    private final List<HttpServer> servers = new ArrayList<>();
    private final Map<String, String> lastHeaders = new ConcurrentHashMap<>();

    @AfterEach
    void tearDown() {
        servers.forEach(s -> s.stop(0));
        RequestContextHolder.resetRequestAttributes();
    }

    /** Réplica falsa que responde a {@code path} com {@code status}/{@code body}. */
    private String fakePeer(String path, int status, String body, long delayMs) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            lastHeaders.put("hops", String.valueOf(exchange.getRequestHeaders().getFirst(AisafeHeaders.PEER_HOPS)));
            lastHeaders.put("key", String.valueOf(exchange.getRequestHeaders().getFirst(AisafeHeaders.SERVICE_KEY)));
            sleep(delayMs);
            boolean match = exchange.getRequestURI().getPath().equals(path);
            int code = match ? status : 404;
            byte[] bytes = (match ? body : "{\"error\":\"not found\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        servers.add(server);
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static String deadPeer() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return "http://localhost:" + s.getLocalPort(); // porta livre: ninguém a escutar
        }
    }

    private static PeerClient client(List<String> peers) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(300)).build());
        factory.setReadTimeout(Duration.ofMillis(500));
        RestClient rest = RestClient.builder().requestFactory(factory).build();
        ResilientExecutor executor = new ResilientExecutor(
                new CircuitBreakerRegistry(3, Duration.ofSeconds(10), Clock.systemUTC()), 0, Duration.ofMillis(1));
        return new PeerClient(peers, rest, executor, new OutboundHeaders("secret", "test-1"), Duration.ofSeconds(3));
    }

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() { };

    @Test
    void findFirstReturnsTheReplicaThatOwnsTheResource() throws IOException {
        String withoutIt = fakePeer("/other", 200, "{}", 0);
        String owner = fakePeer("/api/routes/r1", 200, "{\"id\":\"r1\"}", 0);

        Optional<Map<String, Object>> result = client(List.of(withoutIt, owner)).findFirst("/api/routes/r1", MAP);

        assertTrue(result.isPresent());
        assertEquals("r1", result.get().get("id"));
    }

    @Test
    void findFirstIsEmptyWhenNoPeerHasTheResource() throws IOException {
        String p1 = fakePeer("/x", 200, "{}", 0);
        String p2 = fakePeer("/y", 200, "{}", 0);

        assertTrue(client(List.of(p1, p2)).findFirst("/api/routes/missing", MAP).isEmpty());
    }

    @Test
    void forwardedRequestsCarryHopCountAndServiceKey() throws IOException {
        String peer = fakePeer("/api/routes/r1", 200, "{\"id\":\"r1\"}", 0);

        client(List.of(peer)).findFirst("/api/routes/r1", MAP);

        assertEquals("1", lastHeaders.get("hops"));
        assertEquals("secret", lastHeaders.get("key"));
    }

    @Test
    void collectAggregatesAndCountsUnavailablePeers() throws IOException {
        String p1 = fakePeer("/api/routes/search", 200, "[{\"id\":\"a\"}]", 0);
        String p2 = fakePeer("/api/routes/search", 200, "[{\"id\":\"b\"},{\"id\":\"c\"}]", 0);
        String down = deadPeer();

        MockHttpServletResponse response = bindRequest(null);
        PeerResult<List<Map<String, Object>>> result = client(List.of(p1, p2, down))
                .collect("/api/routes/search", LIST);

        assertEquals(2, result.responses().size());
        assertEquals(1, result.failedPeers());
        assertEquals("true", response.getHeader(AisafeHeaders.PARTIAL_RESPONSE));
        assertEquals("1", response.getHeader(AisafeHeaders.UNAVAILABLE_PEERS));
    }

    @Test
    void slowPeerIsTreatedAsUnavailable() throws IOException {
        String slow = fakePeer("/api/routes/search", 200, "[{\"id\":\"late\"}]", 1500);

        PeerResult<List<Map<String, Object>>> result = client(List.of(slow)).collect("/api/routes/search", LIST);

        assertTrue(result.responses().isEmpty());
        assertEquals(1, result.failedPeers());
    }

    @Test
    void requestsFromAnotherReplicaAreNeverForwardedAgain() throws IOException {
        String peer = fakePeer("/api/routes/r1", 200, "{\"id\":\"r1\"}", 0);
        bindRequest("1"); // o pedido em curso já veio de um peer

        PeerClient client = client(List.of(peer));

        assertTrue(client.isPeerRequest());
        assertTrue(client.findFirst("/api/routes/r1", MAP).isEmpty());
        assertTrue(client.collect("/api/routes/r1", MAP).responses().isEmpty());
    }

    @Test
    void forwardToOwnerSkipsReplicasThatDoNotOwnTheResource() throws IOException {
        String notOwner = fakePeer("/other", 200, "{}", 0);
        String owner = fakePeer("/api/routes/r1", 200, "{\"id\":\"r1\",\"status\":\"INACTIVE\"}", 0);

        Optional<Map> result = client(List.of(notOwner, owner))
                .forwardToOwner(HttpMethod.PATCH, "/api/routes/r1", Map.of("status", "INACTIVE"), Map.class);

        assertTrue(result.isPresent());
        assertEquals("INACTIVE", result.get().get("status"));
    }

    @Test
    void forwardToOwnerPropagatesBusinessErrors() throws IOException {
        String owner = fakePeer("/api/routes/r1", 409, "{\"error\":\"Route is already INACTIVE.\"}", 0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> client(List.of(owner))
                .forwardToOwner(HttpMethod.PATCH, "/api/routes/r1", Map.of("status", "INACTIVE"), Map.class));

        assertEquals(409, ex.getStatusCode().value());
        assertEquals("Route is already INACTIVE.", ex.getReason());
    }

    private static MockHttpServletResponse bindRequest(String hops) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (hops != null) {
            request.addHeader(AisafeHeaders.PEER_HOPS, hops);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        return response;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
