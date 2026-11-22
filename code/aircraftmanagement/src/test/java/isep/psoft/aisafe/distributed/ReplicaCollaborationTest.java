package isep.psoft.aisafe.distributed;

import isep.psoft.aisafe.aircraftmanagement.command.ReadModelSynchronizer;
import isep.psoft.aisafe.aircraftmanagement.query.ReplicatedAircraftModelRepository;
import isep.psoft.aisafe.aircraftmanagement.query.ReplicatedAircraftRepository;
import isep.psoft.aisafe.support.InProcessEventBus;
import isep.sidis.common.http.AisafeHeaders;
import isep.sidis.common.messaging.IntegrationEvent;
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
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Collaboration of two real Aircraft Management replicas (A and B, each with its own H2 database) under the
 * Assignment 2 CQRS design, WITHOUT a broker: committed events travel through {@link InProcessEventBus} (JSON, one
 * delivery per replica, like one RabbitMQ queue per replica). Flight Routes events are injected the same way.
 *
 * <p>Covers: commands stored on the replica that received them (owner), read-your-writes on the owner, every GET
 * answered from the local read model (no peer calls, no X-Partial-Response), uniqueness and model existence checked
 * on the read models, updates forwarded to the owner and converging on both replicas, the unicorns projection,
 * US203/US204/US206 computed from Flight Routes events (no HTTP to Flight Routes), idempotent consumption, and a
 * replica failure (the survivor keeps answering reads, but cannot execute writes owned by the failed replica).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReplicaCollaborationTest {

    private static final String SERVICE_KEY = "it-service-key";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final InProcessEventBus BUS = new InProcessEventBus();
    private static ConfigurableApplicationContext replicaA;
    private static ConfigurableApplicationContext replicaB;
    private static String urlA;
    private static String urlB;
    private static String atcc;
    private static String backoffice;

    @BeforeAll
    static void startReplicas() throws IOException {
        int portA = freePort();
        int portB = freePort();
        urlA = "http://localhost:" + portA;
        urlB = "http://localhost:" + portB;
        replicaA = startReplica("A", portA, urlB);
        replicaB = startReplica("B", portB, urlA);
        JwtTokenProvider tokens = replicaA.getBean(JwtTokenProvider.class);
        atcc = tokens.generateToken("atcc-it", List.of("ROLE_ATCC"));
        backoffice = tokens.generateToken("bo-it", List.of("ROLE_BACKOFFICE_OPERATOR"));
    }

    @AfterAll
    static void stopAll() {
        if (replicaA != null) replicaA.close();
        if (replicaB != null && replicaB.isActive()) replicaB.close();
    }

    // ------------------------------------------------------------------ scenarios

    @Test
    @Order(1)
    void eachReplicaOwnsWhatItReceivesAndChecksModelsOfOtherReplicasOnItsReadModel() throws Exception {
        assertEquals(201, send(backoffice, "POST", urlA + "/api/aircraft-models",
                "{\"modelName\":\"A320\",\"manufacturer\":\"Airbus\",\"seatingCapacity\":180,\"fuelCapacity\":24210,"
                        + "\"maximumRange\":6100,\"cruisingSpeed\":828}").statusCode());
        assertEquals(201, send(backoffice, "POST", urlB + "/api/aircraft-models",
                "{\"modelName\":\"A330\",\"manufacturer\":\"Airbus\",\"seatingCapacity\":300,\"fuelCapacity\":139090,"
                        + "\"maximumRange\":11750,\"cruisingSpeed\":871}").statusCode());

        // CS-TUA on B with A's model, CS-TUC on A with B's model: the model check reads the local read model
        HttpResponse<String> onB = send(atcc, "POST", urlB + "/api/aircrafts",
                "{\"registrationNumber\":\"CS-TUA\",\"modelName\":\"A320\",\"seatingCapacity\":180,\"manufacturingDate\":\"2019-05-14\"}");
        assertEquals(201, onB.statusCode(), onB.body());
        HttpResponse<String> onA = send(atcc, "POST", urlA + "/api/aircrafts",
                "{\"registrationNumber\":\"CS-TUC\",\"modelName\":\"A330\",\"seatingCapacity\":290,\"manufacturingDate\":\"2021-01-11\"}");
        assertEquals(201, onA.statusCode(), onA.body());

        // Read-your-writes on the owner, and the owner is recorded in every read model
        assertEquals(200, send(atcc, "GET", urlB + "/api/aircrafts/CS-TUA", null).statusCode());
        for (ConfigurableApplicationContext replica : List.of(replicaA, replicaB)) {
            ReplicatedAircraftRepository readModel = replica.getBean(ReplicatedAircraftRepository.class);
            assertEquals("it-B", readModel.findById("CS-TUA").orElseThrow().getOwnerInstance());
            assertEquals("it-A", readModel.findById("CS-TUC").orElseThrow().getOwnerInstance());
        }
    }

    @Test
    @Order(2)
    void everyReplicaAnswersFromItsOwnReadModelWithTheDataOfAllReplicas() throws Exception {
        for (String url : List.of(urlA, urlB)) {
            HttpResponse<String> page = send(atcc, "GET", url + "/api/aircrafts", null);
            assertEquals(200, page.statusCode());
            assertEquals(2, json(page).get("totalElements").asInt());
            assertEquals(List.of("CS-TUA", "CS-TUC"), registrations(json(page).get("content")));
            assertTrue(page.headers().firstValue(AisafeHeaders.PARTIAL_RESPONSE).isEmpty());

            assertEquals(11750.0, json(send(atcc, "GET", url + "/api/aircraft-models/A330", null))
                    .get("maximumRange").asDouble());
        }
    }

    @Test
    @Order(3)
    void duplicatesAreDetectedOnTheReadModel() throws Exception {
        assertEquals(409, send(atcc, "POST", urlA + "/api/aircrafts",
                "{\"registrationNumber\":\"CS-TUA\",\"modelName\":\"A320\",\"seatingCapacity\":180,\"manufacturingDate\":\"2019-05-14\"}")
                .statusCode());
        assertEquals(409, send(backoffice, "POST", urlB + "/api/aircraft-models",
                "{\"modelName\":\"A320\",\"manufacturer\":\"Airbus\",\"seatingCapacity\":180,\"fuelCapacity\":1,"
                        + "\"maximumRange\":1,\"cruisingSpeed\":1}").statusCode());
        assertEquals(400, send(atcc, "POST", urlA + "/api/aircrafts",
                "{\"registrationNumber\":\"CS-ZZZ\",\"modelName\":\"B777\",\"seatingCapacity\":300,\"manufacturingDate\":\"2019-05-14\"}")
                .statusCode());
    }

    @Test
    @Order(4)
    void statusChangeIsForwardedToTheOwnerAndBothReadModelsAndMoodsConverge() throws Exception {
        // CS-TUA is owned by B: the PATCH reaches A, which forwards it to B (B3b stays synchronous)
        HttpResponse<String> patched = send(atcc, "PATCH", urlA + "/api/aircrafts/CS-TUA/status",
                "{\"status\":\"UNDER_MAINTENANCE\"}");
        assertEquals(200, patched.statusCode(), patched.body());
        assertEquals("UNDER_MAINTENANCE", json(patched).get("status").asString());

        for (String url : List.of(urlA, urlB)) {
            assertEquals("UNDER_MAINTENANCE", json(send(atcc, "GET", url + "/api/aircrafts/CS-TUA", null))
                    .get("status").asString());
            Map<String, JsonNode> moods = byRegistration(json(send(atcc, "GET", url + "/api/v2/unicorns", null)));
            assertEquals("GROUNDED_AND_GRUMPY", moods.get("CS-TUA").get("mood").asString());
            assertEquals("HAPPY", moods.get("CS-TUC").get("mood").asString());
            assertEquals("read-model", moods.get("CS-TUA").get("source").asString());
            assertFalse(moods.get("CS-TUA").get("timestamp").asString().isBlank());
        }
        assertEquals(1L, replicaA.getBean(ReplicatedAircraftRepository.class).findById("CS-TUA").orElseThrow()
                .getSourceVersion(), "A's read model has the owner's new version");

        // IN_FLIGHT -> UNDER_MAINTENANCE is refused by the owner (A) and the 409 reaches the client of B
        assertEquals(200, send(atcc, "PATCH", urlB + "/api/aircrafts/CS-TUC/status", "{\"status\":\"IN_FLIGHT\"}")
                .statusCode());
        assertEquals(409, send(atcc, "PATCH", urlB + "/api/aircrafts/CS-TUC/status",
                "{\"status\":\"UNDER_MAINTENANCE\"}").statusCode());
        assertEquals("BUSY", byRegistration(json(send(atcc, "GET", urlB + "/api/v2/unicorns", null)))
                .get("CS-TUC").get("mood").asString());
    }

    @Test
    @Order(5)
    void modelUpdateIsForwardedToTheOwnerAndPublishedAsAircraftModelUpdated() throws Exception {
        HttpResponse<String> patched = send(backoffice, "PATCH", urlA + "/api/aircraft-models/A330",
                "{\"maximumRange\":12000}");
        assertEquals(200, patched.statusCode(), patched.body());

        assertEquals(12000.0, replicaA.getBean(ReplicatedAircraftModelRepository.class).findById("A330")
                .orElseThrow().getMaximumRange());
        assertTrue(BUS.delivered().stream().anyMatch(e -> e.eventType().equals("AircraftModelUpdated")
                && e.aggregateId().equals("A330") && e.sourceInstance().equals("it-B")));
    }

    @Test
    @Order(6)
    void flightRoutesEventsFeedCompatibleRoutesOperationalHoursAndTopModels() throws Exception {
        BUS.deliverFromFlightRoutes(route("r-short", "OPO", "LIS", 400.0, 120, "ACTIVE"));
        BUS.deliverFromFlightRoutes(route("r-long", "LIS", "GRU", 8000.0, 250, "ACTIVE"));
        BUS.deliverFromFlightRoutes(route("r-off", "OPO", "FAO", 100.0, 50, "INACTIVE"));
        BUS.deliverFromFlightRoutes(flight("f1", "CS-TUA", "r-short", 55));
        BUS.deliverFromFlightRoutes(flight("f2", "CS-TUA", "r-short", 75));
        IntegrationEvent<Map<String, Object>> third = flight("f3", "CS-TUC", "r-long", 600);
        BUS.deliverFromFlightRoutes(third);
        BUS.deliverFromFlightRoutes(third);   // at-least-once delivery: the duplicate is ignored

        // US203: A320 (6100 km, 180 seats) -> only the short route; A330 (12000 km, 290 seats) -> both active ones
        assertEquals(List.of("r-short"), ids(json(send(atcc, "GET", urlA + "/api/aircrafts/CS-TUA/compatible-routes", null))));
        assertEquals(List.of("r-long", "r-short"),
                ids(json(send(atcc, "GET", urlB + "/api/aircrafts/CS-TUC/compatible-routes", null))));

        // US206: minutes per aircraft from KNOWN_SCHEDULED_FLIGHT (the duplicate did not count twice)
        Map<String, JsonNode> hours = byRegistration(json(send(atcc, "GET", urlB + "/api/aircrafts/operational-hours", null))
                .get("content"), "registrationNumber");
        assertEquals(130, hours.get("CS-TUA").get("totalMinutes").asLong());
        assertEquals(600, hours.get("CS-TUC").get("totalMinutes").asLong());

        // US204: A330 first (600 min), then A320 (130 min)
        JsonNode top = json(send(atcc, "GET", urlA + "/api/aircraft-models/top-utilized", null));
        assertEquals("A330", top.get(0).get("modelName").asString());
        assertEquals("A320", top.get(1).get("modelName").asString());
        assertEquals(130, top.get(1).get("totalMinutes").asLong());
    }

    @Test
    @Order(7)
    void aLateReplicaFillsItsReadModelWhenThePeersAnswerItsSyncRequest() throws Exception {
        // Replica C starts AFTER every event above was published: its queue did not exist, so it saw nothing
        int portC = freePort();
        ConfigurableApplicationContext replicaC = startReplica("C", portC, urlA + "," + urlB);
        try {
            String urlC = "http://localhost:" + portC;
            assertEquals(404, send(atcc, "GET", urlC + "/api/aircrafts/CS-TUA", null).statusCode());

            // AircraftSyncRequested from C (delivered by the broker in production): A and B republish what they own
            replicaA.getBean(ReadModelSynchronizer.class).onSyncRequested("it-C");
            replicaB.getBean(ReadModelSynchronizer.class).onSyncRequested("it-C");

            JsonNode fleet = json(send(atcc, "GET", urlC + "/api/aircrafts", null));
            assertEquals(List.of("CS-TUA", "CS-TUC"), registrations(fleet.get("content")));
            assertEquals(12000.0, json(send(atcc, "GET", urlC + "/api/aircraft-models/A330", null))
                    .get("maximumRange").asDouble(), "current state, after the US201 update");
            assertEquals("GROUNDED_AND_GRUMPY", byRegistration(json(send(atcc, "GET", urlC + "/api/v2/unicorns", null)))
                    .get("CS-TUA").get("mood").asString());

            // A replica ignores its own request; the peers' read models are unchanged by the republication
            replicaA.getBean(ReadModelSynchronizer.class).onSyncRequested("it-A");
            assertEquals(2, json(send(atcc, "GET", urlA + "/api/aircrafts", null)).get("totalElements").asInt());
        } finally {
            replicaC.close();
        }
    }

    @Test
    @Order(8)
    void securityAndHealthProbes() throws Exception {
        HttpResponse<String> noToken = HTTP.send(HttpRequest.newBuilder(URI.create(urlA + "/api/v2/unicorns")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noToken.statusCode());

        // Without messaging (tests) there is no "rabbit" component and the replica is ready
        assertEquals("UP", getJson(urlA + "/actuator/health/liveness").get("status").asString());
        JsonNode readiness = getJson(urlA + "/actuator/health/readiness");
        assertEquals("UP", readiness.get("status").asString());
        assertFalse(readiness.path("components").has("rabbit"), readiness.toString());
    }

    @Test
    @Order(9)
    void whenAReplicaFailsTheOtherKeepsAnsweringReadsFromItsReadModel() throws Exception {
        replicaB.close(); // simulates the failure of replica B

        // CS-TUA (owned by B) is still readable on A, and so is its mood
        assertEquals(200, send(atcc, "GET", urlA + "/api/aircrafts/CS-TUA", null).statusCode());
        HttpResponse<String> unicorns = send(atcc, "GET", urlA + "/api/v2/unicorns", null);
        assertEquals(200, unicorns.statusCode());
        assertEquals(2, json(unicorns).size());

        // ...but it cannot be changed while its owner is down; A's own aircraft can
        assertEquals(503, send(atcc, "PATCH", urlA + "/api/aircrafts/CS-TUA/status", "{\"status\":\"AVAILABLE\"}")
                .statusCode());
        assertEquals(200, send(atcc, "PATCH", urlA + "/api/aircrafts/CS-TUC/status", "{\"status\":\"AVAILABLE\"}")
                .statusCode());
    }

    // ------------------------------------------------------------------ test infrastructure

    private static ConfigurableApplicationContext startReplica(String name, int port, String peerUrl) {
        // Command-line arguments take precedence over application.properties
        return BUS.start(name,
                "--server.port=" + port,
                "--aisafe.instance-id=it-" + name,
                "--spring.datasource.url=jdbc:h2:mem:it-am-" + name + ";DB_CLOSE_DELAY=-1",
                "--aisafe.peers.urls=" + peerUrl,
                "--aisafe.security.service-key=" + SERVICE_KEY,
                "--aisafe.http.read-timeout=2s",
                "--aisafe.http.max-retries=0",
                "--aisafe.bootstrap.enabled=false",
                "--logging.file.name=target/it-logs/replica-" + name + ".log");
    }

    private static IntegrationEvent<Map<String, Object>> route(String id, String origin, String dest, double minRange,
                                                               int minCapacity, String status) {
        Map<String, Object> p = new HashMap<>();
        p.put("routeId", id);
        p.put("originIata", origin);
        p.put("destinationIata", dest);
        p.put("distance", 1000.0);
        p.put("minRange", minRange);
        p.put("minCapacity", minCapacity);
        p.put("estimatedFlightTime", 60);
        p.put("status", status);
        p.put("version", 0);
        p.put("history", List.of());
        return IntegrationEvent.create("RouteCreated", Instant.now(), "flightroutes", "flightroutes-1", id, p);
    }

    private static IntegrationEvent<Map<String, Object>> flight(String id, String registration, String routeId,
                                                                int minutes) {
        Map<String, Object> p = new HashMap<>();
        p.put("flightId", id);
        p.put("aircraftRegistration", registration);
        p.put("routeId", routeId);
        p.put("originIata", "OPO");
        p.put("destinationIata", "LIS");
        p.put("estimatedFlightTime", minutes);
        p.put("date", "2026-11-02");
        p.put("time", "08:00:00");
        p.put("status", "SCHEDULED");
        p.put("version", 0);
        return IntegrationEvent.create("ScheduledFlightCreated", Instant.now(), "flightroutes", "flightroutes-2", id, p);
    }

    private static JsonNode getJson(String url) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
        return JSON.readTree(r.body());
    }

    private static HttpResponse<String> send(String token, String method, String url, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    private static List<String> registrations(JsonNode array) {
        List<String> regs = new ArrayList<>();
        array.forEach(n -> regs.add(n.get("registrationNumber").asString()));
        return regs;
    }

    private static List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(n -> ids.add(n.get("id").asString()));
        return ids.stream().sorted().toList();
    }

    private static Map<String, JsonNode> byRegistration(JsonNode array) {
        return byRegistration(array, "registration");
    }

    private static Map<String, JsonNode> byRegistration(JsonNode array, String field) {
        Map<String, JsonNode> map = new HashMap<>();
        array.forEach(n -> map.put(n.get(field).asString(), n));
        return map;
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
