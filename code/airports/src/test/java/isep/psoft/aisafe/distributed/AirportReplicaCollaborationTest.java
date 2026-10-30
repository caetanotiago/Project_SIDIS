package isep.psoft.aisafe.distributed;

import com.sun.net.httpserver.HttpServer;
import isep.psoft.aisafe.AirportsApplication;
import isep.sidis.common.http.AisafeHeaders;
import isep.sidis.common.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.builder.SpringApplicationBuilder;
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
 * Testes de colaboração entre réplicas do Airports.
 *
 * <p>Arranca DUAS réplicas reais do Airports (A e B, cada uma com a sua H2) configuradas como peers
 * uma da outra, e um stub HTTP do Flight Routes (contrato B5). Cobre: acesso local, forwarding de GET
 * para o peer, agregação, unicidade do IATA entre réplicas, comandos encaminhados para a réplica dona
 * (US106a, US109, US208), US210 e US211 sobre dados de várias réplicas, autenticação entre serviços,
 * RBAC e falha de uma réplica.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AirportReplicaCollaborationTest {

    private static final String SERVICE_KEY = "it-service-key";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static HttpServer flightRoutesStub;
    private static ConfigurableApplicationContext replicaA;
    private static ConfigurableApplicationContext replicaB;
    private static String urlA;
    private static String urlB;
    private static String backoffice;
    private static String atcc;

    @BeforeAll
    static void startReplicas() throws IOException {
        flightRoutesStub = startFlightRoutesStub();
        String flightRoutes = "http://localhost:" + flightRoutesStub.getAddress().getPort();

        int portA = freePort();
        int portB = freePort();
        urlA = "http://localhost:" + portA;
        urlB = "http://localhost:" + portB;

        replicaA = startReplica("A", portA, urlB, flightRoutes);
        replicaB = startReplica("B", portB, urlA, flightRoutes);

        JwtTokenProvider tokens = replicaA.getBean(JwtTokenProvider.class);
        backoffice = tokens.generateToken("bo-it", List.of("ROLE_BACKOFFICE_OPERATOR"));
        atcc = tokens.generateToken("atcc-it", List.of("ROLE_ATCC"));
    }

    @AfterAll
    static void stopAll() {
        if (replicaA != null) replicaA.close();
        if (replicaB != null && replicaB.isActive()) replicaB.close();
        if (flightRoutesStub != null) flightRoutesStub.stop(0);
    }

    // ------------------------------------------------------------------ cenários

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

        // Acesso local direto
        assertEquals(200, send("GET", urlA + "/api/airports/OPO", null, atcc).statusCode());
    }

    @Test
    @Order(2)
    void getByIataIsForwardedToThePeerThatOwnsTheAirport() throws Exception {
        HttpResponse<String> response = send("GET", urlA + "/api/airports/lis", null, atcc);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json(response);
        // Contrato B2 (Flight Routes): iataCode, latitude, longitude e status
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
    void searchAggregatesTheAirportsOfAllReplicas() throws Exception {
        for (String url : List.of(urlA, urlB)) {
            HttpResponse<String> all = send("GET", url + "/api/airports", null, atcc);
            assertEquals(200, all.statusCode());
            assertEquals(List.of("LIS", "MAD", "OPO"), iatas(json(all)));
            assertTrue(all.headers().firstValue(AisafeHeaders.PARTIAL_RESPONSE).isEmpty());
        }

        // Filtros também são aplicados nos peers
        assertEquals(List.of("LIS", "OPO"), iatas(json(send("GET", urlA + "/api/airports?country=portugal", null, atcc))));
        assertEquals(List.of("LIS"), iatas(json(send("GET", urlA + "/api/airports?city=Lisbon", null, atcc))));
        assertEquals(List.of("MAD"), iatas(json(send("GET", urlA + "/api/airports?name=madrid", null, atcc))));
    }

    @Test
    @Order(4)
    void duplicateIataIsDetectedAcrossReplicas() throws Exception {
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
        // US109 pedido em A para um aeroporto guardado em B
        HttpResponse<String> status = send("PATCH", urlA + "/api/airports/LIS/status", "{\"status\":\"UNDER_MAINTENANCE\"}", backoffice);
        assertEquals(200, status.statusCode(), status.body());
        assertEquals("UNDER_MAINTENANCE", json(status).get("status").asString());
        // A alteração ficou na réplica dona (B)
        assertEquals("UNDER_MAINTENANCE", json(send("GET", urlB + "/api/airports/LIS", null, atcc)).get("status").asString());
        assertEquals(400, send("PATCH", urlA + "/api/airports/LIS/status", "{\"status\":\"OPEN\"}", backoffice).statusCode());

        // US106a: certificação via A; repetir → 409 vindo da dona e propagado
        HttpResponse<String> cert = send("POST", urlA + "/api/airports/LIS/certifications", "{\"aircraftModel\":\"A320\"}", atcc);
        assertEquals(201, cert.statusCode(), cert.body());
        assertEquals("A320", json(cert).get("certifiedModels").get(0).asString());
        assertEquals(409, send("POST", urlA + "/api/airports/LIS/certifications", "{\"aircraftModel\":\"a320\"}", atcc).statusCode());

        // US208: atualização parcial via A
        HttpResponse<String> details = send("PATCH", urlA + "/api/airports/LIS", "{\"gates\":60,\"contactEmail\":\"info@lis.pt\"}", backoffice);
        assertEquals(200, details.statusCode(), details.body());
        assertEquals(60, json(details).get("gates").asInt());
        assertEquals("info@lis.pt", json(send("GET", urlB + "/api/airports/LIS", null, atcc)).get("contactEmail").asString());

        // Nenhuma réplica tem o aeroporto → 404
        assertEquals(404, send("PATCH", urlA + "/api/airports/XXX/status", "{\"status\":\"CLOSED\"}", backoffice).statusCode());
    }

    @Test
    @Order(7)
    void groupingUsesTheAirportsOfAllReplicas() throws Exception {
        JsonNode groups = json(send("GET", urlA + "/api/airports/grouped?by=country", null, atcc));

        assertEquals(2, groups.size());
        assertEquals("Portugal", groups.get(0).get("group").asString());
        assertEquals(2, groups.get(0).get("count").asInt()); // OPO (A) + LIS (B)
        assertEquals("Spain", groups.get(1).get("group").asString());

        JsonNode byRegion = json(send("GET", urlB + "/api/airports/grouped?by=region", null, atcc));
        assertEquals("Europe", byRegion.get(0).get("group").asString());
        assertEquals(3, byRegion.get(0).get("count").asInt());

        assertEquals(400, send("GET", urlA + "/api/airports/grouped?by=city", null, atcc).statusCode());
    }

    @Test
    @Order(8)
    void busiestAirportsCombineFlightRoutesCountsWithAirportDetails() throws Exception {
        // Stub do Flight Routes: {"LIS":3,"OPO":2,"MAD":2,"ZZZ":1}
        HttpResponse<String> response = send("GET", urlA + "/api/airports/statistics/busiest?limit=3", null, backoffice);
        assertEquals(200, response.statusCode(), response.body());

        JsonNode ranking = json(response);
        assertEquals(3, ranking.size());
        assertEquals("LIS", ranking.get(0).get("iataCode").asString());
        assertEquals(3, ranking.get(0).get("routeCount").asLong());
        assertEquals("Lisbon", ranking.get(0).get("city").asString()); // detalhes vindos de B
        assertEquals("MAD", ranking.get(1).get("iataCode").asString()); // empate 2-2 → ordem do IATA
        assertEquals("OPO", ranking.get(2).get("iataCode").asString());
        assertEquals(3, ranking.get(2).get("rank").asInt());

        assertEquals(400, send("GET", urlA + "/api/airports/statistics/busiest?limit=0", null, backoffice).statusCode());
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
        assertEquals(List.of("LIS", "MAD"), iatas(json(local)));
    }

    @Test
    @Order(10)
    void rolesAreEnforcedPerEndpoint() throws Exception {
        HttpResponse<String> noToken = HTTP.send(HttpRequest.newBuilder(URI.create(urlA + "/api/airports")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noToken.statusCode());

        // ATCC não regista aeroportos nem altera o estado (US106, US109: Backoffice Operator)
        assertEquals(403, send("POST", urlA + "/api/airports", airport("FNC", "Funchal", "Portugal", 32.69, -16.77), atcc).statusCode());
        assertEquals(403, send("PATCH", urlA + "/api/airports/OPO/status", "{\"status\":\"CLOSED\"}", atcc).statusCode());
        // Backoffice Operator não pesquisa (US108) nem agrupa (US211); ATCC não vê estatísticas (US210)
        assertEquals(403, send("GET", urlA + "/api/airports", null, backoffice).statusCode());
        assertEquals(403, send("GET", urlA + "/api/airports/grouped", null, backoffice).statusCode());
        assertEquals(403, send("GET", urlA + "/api/airports/statistics/busiest", null, atcc).statusCode());
        // Ambos veem os detalhes (US107)
        assertEquals(200, send("GET", urlA + "/api/airports/OPO", null, backoffice).statusCode());
        // Método não previsto → recusado
        assertEquals(403, send("DELETE", urlA + "/api/airports/OPO", null, backoffice).statusCode());

        HttpResponse<String> health = HTTP.send(HttpRequest.newBuilder(URI.create(urlA + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertEquals("UP", json(health).get("status").asString());
    }

    @Test
    @Order(11)
    void whenAReplicaFailsTheOthersKeepServingWithPartialData() throws Exception {
        replicaB.close(); // simula a falha da réplica B

        HttpResponse<String> all = send("GET", urlA + "/api/airports", null, atcc);
        assertEquals(200, all.statusCode());
        assertEquals(List.of("OPO"), iatas(json(all)));
        assertEquals("true", all.headers().firstValue(AisafeHeaders.PARTIAL_RESPONSE).orElse(null));

        // Dados locais continuam acessíveis; dados só existentes em B → 404
        assertEquals(200, send("GET", urlA + "/api/airports/OPO", null, atcc).statusCode());
        assertEquals(404, send("GET", urlA + "/api/airports/LIS", null, atcc).statusCode());

        // Comando para um aeroporto da réplica em baixo: o único peer não responde → 503
        assertEquals(503, send("PATCH", urlA + "/api/airports/LIS/status", "{\"status\":\"CLOSED\"}", backoffice).statusCode());
    }

    // ------------------------------------------------------------------ infraestrutura do teste

    private static ConfigurableApplicationContext startReplica(String name, int port, String peerUrl, String flightRoutes) {
        // Argumentos de linha de comando: têm precedência sobre o application.properties
        return new SpringApplicationBuilder(AirportsApplication.class).run(
                "--server.port=" + port,
                "--aisafe.instance-id=it-" + name,
                "--spring.datasource.url=jdbc:h2:mem:airports-it-" + name + ";DB_CLOSE_DELAY=-1",
                "--aisafe.peers.urls=" + peerUrl,
                "--services.flightroutes.urls=" + flightRoutes,
                "--aisafe.security.service-key=" + SERVICE_KEY,
                "--aisafe.http.read-timeout=2s",
                "--aisafe.http.max-retries=0",
                "--aisafe.bootstrap.enabled=false",
                "--logging.file.name=target/it-logs/airports-" + name + ".log");
    }

    /** Stub do Flight Routes: só o endpoint de contagens do contrato B5. */
    private static HttpServer startFlightRoutesStub() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/routes/statistics/count-by-airport", exchange -> {
            byte[] bytes = "{\"LIS\":3,\"OPO\":2,\"MAD\":2,\"ZZZ\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return server;
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
