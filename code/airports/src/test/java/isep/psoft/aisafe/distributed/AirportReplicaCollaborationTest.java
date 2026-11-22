package isep.psoft.aisafe.distributed;

import isep.psoft.aisafe.airports.events.external.FlightRouteEventPayload;
import isep.psoft.aisafe.airports.query.external.RouteCountProjection;
import isep.psoft.aisafe.support.InProcessEventBus;
import isep.sidis.common.messaging.IntegrationEvent;
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
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AirportReplicaCollaborationTest {
    private static final String SERVICE_KEY = "it-service-key";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final InProcessEventBus BUS = new InProcessEventBus();

    private static ConfigurableApplicationContext replicaA;
    private static ConfigurableApplicationContext replicaB;
    private static String urlA;
    private static String urlB;
    private static String backoffice;
    private static String atcc;

    @BeforeAll
    static void startReplicas() throws IOException {
        int portA = freePort();
        int portB = freePort();
        urlA = "http://localhost:" + portA;
        urlB = "http://localhost:" + portB;

        replicaA = startReplica("A", portA, urlB);
        replicaB = startReplica("B", portB, urlA);

        JwtTokenProvider tokens = replicaA.getBean(JwtTokenProvider.class);
        backoffice = tokens.generateToken("bo-it", List.of("ROLE_BACKOFFICE_OPERATOR"));
        atcc = tokens.generateToken("atcc-it", List.of("ROLE_ATCC"));
    }

    @AfterAll
    static void stopAll() {
        if (replicaA != null) replicaA.close();
        if (replicaB != null && replicaB.isActive()) replicaB.close();
    }

    @Test
    @Order(1)
    void eachReplicaStoresTheAirportsItReceives() throws Exception {
        HttpResponse<String> opo = send("POST", urlA + "/api/airports", airport("OPO", "Porto", "Portugal", 41.2481, -8.6814), backoffice);
        assertEquals(201, opo.statusCode(), opo.body());
        assertTrue(opo.headers().firstValue("Location").orElse("").endsWith("/api/airports/OPO"));

        HttpResponse<String> lis = send("POST", urlB + "/api/airports", airport("lis", "Lisbon", "Portugal", 38.7742, -9.1342), backoffice);
        assertEquals(201, lis.statusCode(), lis.body());
        assertEquals("LIS", json(lis).get("iataCode").asString());

        HttpResponse<String> mad = send("POST", urlB + "/api/airports", airport("MAD", "Madrid", "Spain", 40.4719, -3.5626), backoffice);
        assertEquals(201, mad.statusCode(), mad.body());

        assertEquals(200, send("GET", urlA + "/api/airports/OPO", null, atcc).statusCode());
    }

    @Test
    @Order(2)
    void getByIataIsAnsweredFromTheLocalReadModel() throws Exception {
        HttpResponse<String> response = send("GET", urlA + "/api/airports/lis", null, atcc);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json(response);
        assertEquals("LIS", body.get("iataCode").asString());
        assertEquals(38.7742, body.get("latitude").asDouble(), 1e-6);
        assertEquals(-9.1342, body.get("longitude").asDouble(), 1e-6);
        assertEquals("OPERATIONAL", body.get("status").asString());
        assertTrue(response.headers().firstValue(AisafeHeaders.CORRELATION_ID).isPresent());

        assertEquals(404, send("GET", urlA + "/api/airports/XXX", null, atcc).statusCode());
        assertEquals(400, send("GET", urlA + "/api/airports/X1", null, atcc).statusCode());
    }

    @Test
    @Order(3)
    void everyReplicaSearchesTheAirportsOfAllReplicas() throws Exception {
        for (String url : List.of(urlA, urlB)) {
            HttpResponse<String> all = send("GET", url + "/api/airports", null, atcc);
            assertEquals(200, all.statusCode());
            assertEquals(List.of("LIS", "MAD", "OPO"), iatas(json(all)));
            assertTrue(all.headers().firstValue(AisafeHeaders.PARTIAL_RESPONSE).isEmpty());
        }

        assertEquals(List.of("LIS", "OPO"), iatas(json(send("GET", urlA + "/api/airports?country=portugal", null, atcc))));
        assertEquals(List.of("LIS"), iatas(json(send("GET", urlA + "/api/airports?city=Lisbon", null, atcc))));
        assertEquals(List.of("MAD"), iatas(json(send("GET", urlA + "/api/airports?name=madrid", null, atcc))));
    }

    @Test
    @Order(4)
    void duplicateIataIsDetectedInTheReadModel() throws Exception {
        HttpResponse<String> response = send("POST", urlA + "/api/airports",
                airport("LIS", "Lisbon", "Portugal", 38.7742, -9.1342), backoffice);

        assertEquals(409, response.statusCode(), response.body());
    }

    @Test
    @Order(5)
    void invalidRegistrationIsRejected() throws Exception {
        String noRunways = "{\"iataCode\":\"FNC\",\"name\":\"Madeira\",\"city\":\"Funchal\",\"country\":\"Portugal\","
                + "\"timezone\":\"Atlantic/Madeira\",\"latitude\":32.69,\"longitude\":-16.77,\"runways\":[]}";
        assertEquals(400, send("POST", urlA + "/api/airports", noRunways, backoffice).statusCode());

        String badTimezone = airport("FNC", "Funchal", "Portugal", 32.69, -16.77).replace("Europe/Lisbon", "Madeira/Funchal");
        assertEquals(400, send("POST", urlA + "/api/airports", badTimezone, backoffice).statusCode());
    }

    @Test
    @Order(6)
    void commandsAreForwardedToTheOwnerReplica() throws Exception {
        HttpResponse<String> status = send("PATCH", urlA + "/api/airports/LIS/status", "{\"status\":\"UNDER_MAINTENANCE\"}", backoffice);
        assertEquals(200, status.statusCode(), status.body());
        assertEquals("UNDER_MAINTENANCE", json(status).get("status").asString());
        assertEquals("UNDER_MAINTENANCE", json(send("GET", urlB + "/api/airports/LIS", null, atcc)).get("status").asString());
        assertEquals("UNDER_MAINTENANCE", json(send("GET", urlA + "/api/airports/LIS", null, atcc)).get("status").asString());
        assertTrue(BUS.delivered().stream().anyMatch(e -> e.eventType().equals("AirportStatusChanged")
                && e.aggregateId().equals("LIS") && e.sourceInstance().equals("it-B")));
        assertEquals(400, send("PATCH", urlA + "/api/airports/LIS/status", "{\"status\":\"OPEN\"}", backoffice).statusCode());

        HttpResponse<String> cert = send("POST", urlA + "/api/airports/LIS/certifications", "{\"aircraftModel\":\"A320\"}", atcc);
        assertEquals(201, cert.statusCode(), cert.body());
        assertEquals("A320", json(cert).get("certifiedModels").get(0).asString());
        assertEquals(409, send("POST", urlA + "/api/airports/LIS/certifications", "{\"aircraftModel\":\"a320\"}", atcc).statusCode());

        HttpResponse<String> details = send("PATCH", urlA + "/api/airports/LIS", "{\"gates\":60,\"contactEmail\":\"info@lis.pt\"}", backoffice);
        assertEquals(200, details.statusCode(), details.body());
        assertEquals(60, json(details).get("gates").asInt());
        assertEquals("info@lis.pt", json(send("GET", urlB + "/api/airports/LIS", null, atcc)).get("contactEmail").asString());
        assertEquals(60, json(send("GET", urlA + "/api/airports/LIS", null, atcc)).get("gates").asInt());

        assertEquals(404, send("PATCH", urlA + "/api/airports/XXX/status", "{\"status\":\"CLOSED\"}", backoffice).statusCode());
    }

    @Test
    @Order(7)
    void groupingUsesTheAirportsOfAllReplicas() throws Exception {
        JsonNode groups = json(send("GET", urlA + "/api/airports/grouped?by=country", null, atcc));

        assertEquals(2, groups.size());
        assertEquals("Portugal", groups.get(0).get("group").asString());
        assertEquals(2, groups.get(0).get("count").asInt());
        assertEquals("Spain", groups.get(1).get("group").asString());

        JsonNode byRegion = json(send("GET", urlB + "/api/airports/grouped?by=region", null, atcc));
        assertEquals("Europe", byRegion.get(0).get("group").asString());
        assertEquals(3, byRegion.get(0).get("count").asInt());

        assertEquals(400, send("GET", urlA + "/api/airports/grouped?by=city", null, atcc).statusCode());
    }

    @Test
    @Order(8)
    void busiestAirportsAreComputedFromFlightRoutesEvents() throws Exception {
        routeCreated("r1", "LIS", "OPO");
        routeCreated("r2", "LIS", "MAD");
        routeCreated("r3", "LIS", "ZZZ");
        IntegrationEvent<FlightRouteEventPayload> r4 = routeCreated("r4", "OPO", "MAD");
        assertFalse(replicaA.getBean(RouteCountProjection.class).apply(r4));

        HttpResponse<String> response = send("GET", urlA + "/api/airports/statistics/busiest?limit=3", null, backoffice);
        assertEquals(200, response.statusCode(), response.body());

        JsonNode ranking = json(response);
        assertEquals(3, ranking.size());
        assertEquals("LIS", ranking.get(0).get("iataCode").asString());
        assertEquals(3, ranking.get(0).get("routeCount").asLong());
        assertEquals("Lisbon", ranking.get(0).get("city").asString());
        assertEquals("MAD", ranking.get(1).get("iataCode").asString());
        assertEquals("OPO", ranking.get(2).get("iataCode").asString());
        assertEquals(3, ranking.get(2).get("rank").asInt());

        assertEquals(400, send("GET", urlA + "/api/airports/statistics/busiest?limit=0", null, backoffice).statusCode());
        assertEquals("LIS", json(send("GET", urlB + "/api/airports/statistics/busiest?limit=1", null, backoffice))
                .get(0).get("iataCode").asString());
    }

    @Test
    @Order(9)
    void peerRequestsRequireTheServiceKeyAndAreAnsweredLocally() throws Exception {
        assertEquals(401, send("GET", urlB + "/api/airports", null, atcc,
                Map.of(AisafeHeaders.PEER_HOPS, "1")).statusCode());
        assertEquals(401, send("GET", urlB + "/api/airports", null, atcc,
                Map.of(AisafeHeaders.PEER_HOPS, "1", AisafeHeaders.SERVICE_KEY, "wrong")).statusCode());
        assertEquals(400, send("GET", urlB + "/api/airports", null, atcc,
                Map.of(AisafeHeaders.PEER_HOPS, "2", AisafeHeaders.SERVICE_KEY, SERVICE_KEY)).statusCode());

        HttpResponse<String> local = send("GET", urlB + "/api/airports", null, atcc,
                Map.of(AisafeHeaders.PEER_HOPS, "1", AisafeHeaders.SERVICE_KEY, SERVICE_KEY));
        assertEquals(200, local.statusCode());
        assertEquals(List.of("LIS", "MAD", "OPO"), iatas(json(local)));
    }

    @Test
    @Order(10)
    void rolesAreEnforcedPerEndpoint() throws Exception {
        HttpResponse<String> noToken = HTTP.send(HttpRequest.newBuilder(URI.create(urlA + "/api/airports")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noToken.statusCode());

        assertEquals(403, send("POST", urlA + "/api/airports", airport("FNC", "Funchal", "Portugal", 32.69, -16.77), atcc).statusCode());
        assertEquals(403, send("PATCH", urlA + "/api/airports/OPO/status", "{\"status\":\"CLOSED\"}", atcc).statusCode());
        assertEquals(403, send("GET", urlA + "/api/airports", null, backoffice).statusCode());
        assertEquals(403, send("GET", urlA + "/api/airports/grouped", null, backoffice).statusCode());
        assertEquals(403, send("GET", urlA + "/api/airports/statistics/busiest", null, atcc).statusCode());
        assertEquals(200, send("GET", urlA + "/api/airports/OPO", null, backoffice).statusCode());
        assertEquals(403, send("DELETE", urlA + "/api/airports/OPO", null, backoffice).statusCode());

        HttpResponse<String> health = HTTP.send(HttpRequest.newBuilder(URI.create(urlA + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertEquals("UP", json(health).get("status").asString());
    }

    @Test
    @Order(11)
    void eventualConsistencyWhileAReplicaIsDisconnected() throws Exception {
        BUS.disconnect("B");
        assertEquals(200, send("PATCH", urlA + "/api/airports/OPO/status", "{\"status\":\"CLOSED\"}", backoffice).statusCode());
        assertEquals("CLOSED", json(send("GET", urlA + "/api/airports/OPO", null, atcc)).get("status").asString());
        assertEquals("OPERATIONAL", json(send("GET", urlB + "/api/airports/OPO", null, atcc)).get("status").asString());

        BUS.reconnect("B");
        assertEquals(200, send("PATCH", urlA + "/api/airports/OPO/status", "{\"status\":\"OPERATIONAL\"}", backoffice).statusCode());
        assertEquals("OPERATIONAL", json(send("GET", urlB + "/api/airports/OPO", null, atcc)).get("status").asString());
    }

    @Test
    @Order(12)
    void whenAReplicaFailsTheOthersKeepServingFromTheirReadModel() throws Exception {
        replicaB.close();

        HttpResponse<String> all = send("GET", urlA + "/api/airports", null, atcc);
        assertEquals(200, all.statusCode());
        assertEquals(List.of("LIS", "MAD", "OPO"), iatas(json(all)));
        assertTrue(all.headers().firstValue(AisafeHeaders.PARTIAL_RESPONSE).isEmpty());
        assertEquals(200, send("GET", urlA + "/api/airports/LIS", null, atcc).statusCode());

        assertEquals(503, send("PATCH", urlA + "/api/airports/LIS/status", "{\"status\":\"CLOSED\"}", backoffice).statusCode());
    }

    private static IntegrationEvent<FlightRouteEventPayload> routeCreated(String id, String origin, String destination) {
        IntegrationEvent<FlightRouteEventPayload> event = IntegrationEvent.create("RouteCreated", Instant.now(),
                "flightroutes", "flightroutes-it", id, new FlightRouteEventPayload(id, origin, destination, "ACTIVE", 0L));
        for (ConfigurableApplicationContext replica : List.of(replicaA, replicaB)) {
            assertTrue(replica.getBean(RouteCountProjection.class).apply(event));
        }
        return event;
    }

    private static ConfigurableApplicationContext startReplica(String name, int port, String peerUrl) {
        return BUS.start(name,
                "--server.port=" + port,
                "--aisafe.instance-id=it-" + name,
                "--spring.datasource.url=jdbc:h2:mem:airports-it-" + name + ";DB_CLOSE_DELAY=-1",
                "--aisafe.peers.urls=" + peerUrl,
                "--aisafe.security.service-key=" + SERVICE_KEY,
                "--aisafe.http.read-timeout=2s",
                "--aisafe.http.max-retries=0",
                "--aisafe.bootstrap.enabled=false",
                "--logging.file.name=target/it-logs/airports-" + name + ".log");
    }

    private static String airport(String iata, String city, String country, double lat, double lon) {
        return "{\"iataCode\":\"" + iata + "\",\"name\":\"" + city + " Airport\",\"city\":\"" + city + "\","
                + "\"country\":\"" + country + "\",\"region\":\"Europe\",\"timezone\":\"Europe/Lisbon\","
                + "\"latitude\":" + lat + ",\"longitude\":" + lon + ","
                + "\"runways\":[{\"name\":\"17/35\",\"lengthMeters\":3000,\"orientation\":170}]}";
    }

    private static HttpResponse<String> send(String method, String url, String body, String token) throws Exception {
        return send(method, url, body, token, Map.of());
    }

    private static HttpResponse<String> send(String method, String url, String body, String token,
                                             Map<String, String> headers) throws Exception {
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

    private static List<String> iatas(JsonNode array) {
        List<String> codes = new ArrayList<>();
        array.forEach(n -> codes.add(n.get("iataCode").asString()));
        return codes;
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
