package isep.psoft.aisafe.distributed;

import com.sun.net.httpserver.HttpServer;
import isep.psoft.aisafe.flightroutes.query.ReplicatedRouteRepository;
import isep.psoft.aisafe.support.InProcessEventBus;
import isep.sidis.common.http.AisafeHeaders;
import isep.sidis.common.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Collaboration of two real Flight Routes replicas (A and B, each with its own H2 database) under the Assignment 2
 * CQRS design, WITHOUT a broker: committed events travel through {@link InProcessEventBus} (JSON, one delivery per
 * replica, like one RabbitMQ queue per replica). Airports and Aircraft Management are HTTP stubs.
 *
 * <p>Covers: commands stored on the replica that received them (owner), read-your-writes on the owner, every GET
 * answered from the local read model (no peer calls, no X-Partial-Response), uniqueness checked on the read model,
 * updates forwarded to the owner and converging on both replicas, cross-replica data used by commands, service-key
 * authentication of peer requests, liveness/readiness, and a replica failure (the survivor keeps answering reads,
 * but cannot execute writes owned by the failed replica).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReplicaCollaborationTest {

    private static final String SERVICE_KEY = "it-service-key";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final InProcessEventBus BUS = new InProcessEventBus();
    private static HttpServer remoteServices;
    private static ConfigurableApplicationContext replicaA;
    private static ConfigurableApplicationContext replicaB;
    private static String urlA;
    private static String urlB;
    private static String token;

    private static String routeOnB;     // OPO -> LIS, owned by B
    private static String routeOnA;     // LIS -> MAD, owned by A
    private static String flightOnA;    // flight owned by A that uses B's route

    @BeforeAll
    static void startReplicas() throws IOException {
        remoteServices = startRemoteServicesStub();
        String remotes = "http://localhost:" + remoteServices.getAddress().getPort();
        int portA = freePort();
        int portB = freePort();
        urlA = "http://localhost:" + portA;
        urlB = "http://localhost:" + portB;
        replicaA = startReplica("A", portA, urlB, remotes);
        replicaB = startReplica("B", portB, urlA, remotes);
        token = replicaA.getBean(JwtTokenProvider.class).generateToken("atcc-it", List.of("ROLE_ATCC"));
    }

    @AfterAll
    static void stopAll() {
        if (replicaA != null) replicaA.close();
        if (replicaB != null && replicaB.isActive()) replicaB.close();
        if (remoteServices != null) remoteServices.stop(0);
    }

    // ------------------------------------------------------------------ scenarios

    @Test
    @Order(1)
    void eachReplicaOwnsTheRoutesItReceivesAndReadsItsOwnWritesImmediately() throws Exception {
        HttpResponse<String> created = send("POST", urlB + "/api/routes",
                "{\"originIATA\":\"OPO\",\"destIATA\":\"LIS\",\"estimatedFlightTime\":55,\"minRange\":400,\"minCapacity\":120}");
        assertEquals(201, created.statusCode(), created.body());
        routeOnB = json(created).get("id").asString();

        HttpResponse<String> createdA = send("POST", urlA + "/api/routes",
                "{\"originIATA\":\"LIS\",\"destIATA\":\"MAD\",\"estimatedFlightTime\":75,\"minRange\":600,\"minCapacity\":150}");
        assertEquals(201, createdA.statusCode(), createdA.body());
        routeOnA = json(createdA).get("id").asString();

        assertEquals(200, send("GET", urlB + "/api/routes/" + routeOnB, null).statusCode());
        assertEquals("it-B", replicaB.getBean(ReplicatedRouteRepository.class).findById(routeOnB)
                .orElseThrow().getOwnerInstance());
    }

    @Test
    @Order(2)
    void aRouteOwnedByThePeerIsReadFromTheLocalReadModel() throws Exception {
        HttpResponse<String> response = send("GET", urlA + "/api/routes/" + routeOnB, null);
        assertEquals(200, response.statusCode());
        assertEquals(routeOnB, json(response).get("id").asString());
        assertEquals("OPO", json(response).get("originIATA").asString());
        assertTrue(response.headers().firstValue(AisafeHeaders.CORRELATION_ID).isPresent());
        // A holds B's route in its read model, but not in its write model (B owns it)
        assertTrue(replicaA.getBean(ReplicatedRouteRepository.class).existsById(routeOnB));
    }

    @Test
    @Order(3)
    void everyReplicaAnswersSearchesWithTheRoutesOfAllReplicas() throws Exception {
        for (String url : List.of(urlA, urlB)) {
            HttpResponse<String> response = send("GET", url + "/api/routes/search", null);
            assertEquals(200, response.statusCode());
            assertEquals(List.of(routeOnA, routeOnB).stream().sorted().toList(), ids(json(response)));
            assertTrue(response.headers().firstValue(AisafeHeaders.PARTIAL_RESPONSE).isEmpty());
        }
    }

    @Test
    @Order(4)
    void duplicateRouteIsDetectedOnTheReadModel() throws Exception {
        HttpResponse<String> response = send("POST", urlA + "/api/routes",
                "{\"originIATA\":\"OPO\",\"destIATA\":\"LIS\",\"estimatedFlightTime\":55,\"minRange\":400,\"minCapacity\":120}");
        assertEquals(400, response.statusCode(), response.body());
    }

    @Test
    @Order(5)
    void updateIsForwardedToTheOwnerAndBothReadModelsConverge() throws Exception {
        HttpResponse<String> patched = send("PATCH", urlA + "/api/routes/" + routeOnB, "{\"minCapacity\":130}");
        assertEquals(200, patched.statusCode(), patched.body());
        assertEquals(130, json(patched).get("minCapacity").asInt());

        for (String url : List.of(urlA, urlB)) {
            assertEquals(130, json(send("GET", url + "/api/routes/" + routeOnB, null)).get("minCapacity").asInt());
        }
        assertEquals(1L, replicaA.getBean(ReplicatedRouteRepository.class).findById(routeOnB).orElseThrow()
                .getSourceVersion(), "A's read model has the owner's new version");

        // The history travels in the events, so A answers it from its own read model
        HttpResponse<String> history = send("GET", urlA + "/api/routes/" + routeOnB + "/history", null);
        assertEquals(200, history.statusCode());
        assertEquals(2, json(history).size());
        assertEquals(120, json(history).get(1).get("previousMinCapacity").asInt());

        // Business error of the owner is propagated (route already active -> 409)
        assertEquals(409, send("PATCH", urlA + "/api/routes/" + routeOnB, "{\"status\":\"ACTIVE\"}").statusCode());
    }

    @Test
    @Order(6)
    void aFlightCanBeScheduledOnAReplicaThatDoesNotOwnTheRoute() throws Exception {
        HttpResponse<String> created = send("POST", urlA + "/api/scheduled-flights",
                "{\"aircraftRegistration\":\"CS-TUA\",\"routeID\":\"" + routeOnB + "\",\"date\":\"2026-11-02\",\"time\":\"08:00:00\"}");
        assertEquals(201, created.statusCode(), created.body());
        flightOnA = json(created).get("id").asString();

        // Same aircraft, same time, on the other replica -> conflict found in B's flight read model
        HttpResponse<String> conflict = send("POST", urlB + "/api/scheduled-flights",
                "{\"aircraftRegistration\":\"CS-TUA\",\"routeID\":\"" + routeOnA + "\",\"date\":\"2026-11-02\",\"time\":\"08:00:00\"}");
        assertEquals(409, conflict.statusCode(), conflict.body());

        // The flight (owned by A) is read from B's read model, with links that point to B
        HttpResponse<String> fromB = send("GET", urlB + "/api/scheduled-flights/" + flightOnA, null);
        assertEquals(200, fromB.statusCode());
        assertEquals(routeOnB, json(fromB).get("routeID").asString());
        assertTrue(json(fromB).get("_links").get("self").get("href").asString().startsWith(urlB));
    }

    @Test
    @Order(7)
    void popularityAndOperationalHoursCountTheFlightsOfAllReplicas() throws Exception {
        // Route owned by B, flight owned by A: popularity seen from B counts A's flight
        JsonNode active = json(send("GET", urlB + "/api/routes/active?sortBy=popularity", null));
        assertEquals(routeOnB, active.get(0).get("id").asString());
        assertEquals(1, active.get(0).get("usageCount").asLong());

        // Service-only endpoint (aisafe.security.service-only-paths): the test acts as the consumer service
        JsonNode hours = json(send("GET", urlB + "/api/scheduled-flights/operational-hours", null,
                Map.of(AisafeHeaders.SERVICE_KEY, SERVICE_KEY)));
        assertEquals("CS-TUA", hours.get("content").get(0).get("registrationNumber").asString());
        assertEquals(55, hours.get("content").get(0).get("totalMinutes").asLong());

        JsonNode distance = json(send("GET", urlA + "/api/routes/network/total-distance", null));
        assertTrue(distance.get("totalDistanceKm").asDouble() > 0);
    }

    @Test
    @Order(8)
    void alternativeRoutesCombineLegsOwnedByDifferentReplicas() throws Exception {
        JsonNode itineraries = json(send("GET", urlA + "/api/routes/alternatives?origin=OPO&dest=MAD", null));
        assertEquals(1, itineraries.size());
        assertEquals(1, itineraries.get(0).get("numberOfStops").asInt());
        assertEquals(routeOnB, itineraries.get(0).get("legs").get(0).get("routeID").asString());
        assertEquals(routeOnA, itineraries.get(0).get("legs").get(1).get("routeID").asString());
    }

    @Test
    @Order(9)
    void peerRequestsRequireTheServiceKey() throws Exception {
        // Without the key: a client cannot pretend to be a replica
        assertEquals(401, send("GET", urlB + "/api/routes/search", null,
                Map.of(AisafeHeaders.PEER_HOPS, "1")).statusCode());
        // Wrong key
        assertEquals(401, send("GET", urlB + "/api/routes/search", null,
                Map.of(AisafeHeaders.PEER_HOPS, "1", AisafeHeaders.SERVICE_KEY, "wrong")).statusCode());
        // Hops above the maximum (protection against forwarding loops)
        assertEquals(400, send("GET", urlB + "/api/routes/search", null,
                Map.of(AisafeHeaders.PEER_HOPS, "2", AisafeHeaders.SERVICE_KEY, SERVICE_KEY)).statusCode());
        // With the right key the replica answers from its read model, like for any client
        HttpResponse<String> local = send("GET", urlB + "/api/routes/search", null,
                Map.of(AisafeHeaders.PEER_HOPS, "1", AisafeHeaders.SERVICE_KEY, SERVICE_KEY));
        assertEquals(200, local.statusCode());
        assertEquals(List.of(routeOnA, routeOnB).stream().sorted().toList(), ids(json(local)));
    }

    @Test
    @Order(10)
    void securityAndHealthProbes() throws Exception {
        HttpResponse<String> noToken = HTTP.send(HttpRequest.newBuilder(URI.create(urlA + "/api/routes/search")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noToken.statusCode());

        String backoffice = replicaA.getBean(JwtTokenProvider.class)
                .generateToken("bo-it", List.of("ROLE_BACKOFFICE_OPERATOR"));
        HttpResponse<String> wrongRole = HTTP.send(HttpRequest.newBuilder(URI.create(urlA + "/api/routes/search"))
                .header("Authorization", "Bearer " + backoffice).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, wrongRole.statusCode());

        JsonNode health = getJson(urlA + "/actuator/health", 200);
        assertEquals("UP", health.get("status").asString());
        assertTrue(health.get("components").has("peers"), health.toString());

        // Liveness (used by scripts/start-all.*) and readiness; without messaging there is no "rabbit" component
        assertEquals("UP", getJson(urlA + "/actuator/health/liveness", 200).get("status").asString());
        JsonNode readiness = getJson(urlA + "/actuator/health/readiness", 200);
        assertEquals("UP", readiness.get("status").asString());
        assertFalse(readiness.path("components").has("rabbit"), readiness.toString());
    }

    @Test
    @Order(11)
    void whenAReplicaFailsTheOtherKeepsAnsweringReadsFromItsReadModel() throws Exception {
        replicaB.close(); // simulates the failure of replica B

        HttpResponse<String> search = send("GET", urlA + "/api/routes/search", null);
        assertEquals(200, search.statusCode());
        assertEquals(List.of(routeOnA, routeOnB).stream().sorted().toList(), ids(json(search)));
        assertTrue(search.headers().firstValue(AisafeHeaders.PARTIAL_RESPONSE).isEmpty());

        // B's route is still readable on A (read model), but cannot be changed while its owner is down
        assertEquals(200, send("GET", urlA + "/api/routes/" + routeOnB, null).statusCode());
        assertEquals(503, send("PATCH", urlA + "/api/routes/" + routeOnB, "{\"minCapacity\":140}").statusCode());

        // Routes owned by the surviving replica can still be changed
        assertEquals(200, send("PATCH", urlA + "/api/routes/" + routeOnA, "{\"minCapacity\":160}").statusCode());
    }

    // ------------------------------------------------------------------ test infrastructure

    private static ConfigurableApplicationContext startReplica(String name, int port, String peerUrl, String remotes) {
        // Command-line arguments take precedence over application.properties
        return BUS.start(name,
                "--server.port=" + port,
                "--aisafe.instance-id=it-" + name,
                "--spring.datasource.url=jdbc:h2:mem:it-" + name + ";DB_CLOSE_DELAY=-1",
                "--aisafe.peers.urls=" + peerUrl,
                "--services.aircraft.urls=" + remotes,
                "--services.airports.urls=" + remotes,
                "--aisafe.security.service-key=" + SERVICE_KEY,
                "--aisafe.http.read-timeout=2s",
                "--aisafe.http.max-retries=0",
                "--aisafe.bootstrap.enabled=false",
                "--logging.file.name=target/it-logs/replica-" + name + ".log");
    }

    /** Stub of the Airports and Aircraft Management services (same server, different paths). */
    static HttpServer startRemoteServicesStub() throws IOException {
        Map<String, String> airports = Map.of(
                "OPO", "{\"iataCode\":\"OPO\",\"latitude\":41.2481,\"longitude\":-8.6814,\"status\":\"OPERATIONAL\"}",
                "LIS", "{\"iataCode\":\"LIS\",\"latitude\":38.7742,\"longitude\":-9.1342,\"status\":\"OPERATIONAL\"}",
                "MAD", "{\"iataCode\":\"MAD\",\"latitude\":40.4719,\"longitude\":-3.5626,\"status\":\"OPERATIONAL\"}");
        Map<String, String> other = Map.of(
                "/api/aircrafts/CS-TUA", "{\"registrationNumber\":\"CS-TUA\",\"modelName\":\"A320\",\"seatingCapacity\":180,\"status\":\"AVAILABLE\"}",
                "/api/aircraft-models/A320", "{\"modelName\":\"A320\",\"maximumRange\":6100}");
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = path.startsWith("/api/airports/") ? airports.get(path.substring("/api/airports/".length())) : other.get(path);
            byte[] bytes = (body == null ? "{\"error\":\"not found\"}" : body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(body == null ? 404 : 200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return server;
    }

    private static JsonNode getJson(String url, int expectedStatus) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, r.statusCode(), r.body());
        return JSON.readTree(r.body());
    }

    private static HttpResponse<String> send(String method, String url, String body) throws Exception {
        return send(method, url, body, Map.of());
    }

    private static HttpResponse<String> send(String method, String url, String body, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(b::header);
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    private static List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(n -> ids.add(n.get("id").asString()));
        return ids.stream().sorted().toList();
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
