package isep.psoft.aisafe.distributed;

import com.sun.net.httpserver.HttpServer;
import isep.psoft.aisafe.FlightRoutesApplication;
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
 * Testes de colaboração entre réplicas (semana 4: "Testing Component Collaboration, Cloning").
 *
 * <p>Arranca DUAS réplicas reais do Flight Routes (A e B, cada uma com a sua H2) configuradas como
 * peers uma da outra, e stubs HTTP dos serviços Airports e Aircraft Management. Cobre:
 * acesso local, forwarding de GET para o peer, agregação, comando encaminhado para a réplica dona,
 * dados de uma réplica usados noutra, autenticação entre serviços e falha de uma réplica.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReplicaCollaborationTest {

    private static final String SERVICE_KEY = "it-service-key";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static HttpServer remoteServices;
    private static ConfigurableApplicationContext replicaA;
    private static ConfigurableApplicationContext replicaB;
    private static String urlA;
    private static String urlB;
    private static String token;

    private static String routeOnB;     // OPO -> LIS, guardada em B
    private static String routeOnA;     // LIS -> MAD, guardada em A
    private static String flightOnA;    // voo guardado em A que usa a rota de B

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

    // ------------------------------------------------------------------ cenários

    @Test
    @Order(1)
    void eachReplicaStoresTheRoutesItReceives() throws Exception {
        HttpResponse<String> created = send("POST", urlB + "/api/routes",
                "{\"originIATA\":\"OPO\",\"destIATA\":\"LIS\",\"estimatedFlightTime\":55,\"minRange\":400,\"minCapacity\":120}");
        assertEquals(201, created.statusCode(), created.body());
        routeOnB = json(created).get("id").asString();

        HttpResponse<String> createdA = send("POST", urlA + "/api/routes",
                "{\"originIATA\":\"LIS\",\"destIATA\":\"MAD\",\"estimatedFlightTime\":75,\"minRange\":600,\"minCapacity\":150}");
        assertEquals(201, createdA.statusCode(), createdA.body());
        routeOnA = json(createdA).get("id").asString();

        // Acesso local direto (cenário 1 da semana 4)
        assertEquals(200, send("GET", urlB + "/api/routes/" + routeOnB, null).statusCode());
    }

    @Test
    @Order(2)
    void getByIdIsForwardedToThePeerThatOwnsTheData() throws Exception {
        HttpResponse<String> response = send("GET", urlA + "/api/routes/" + routeOnB, null);

        assertEquals(200, response.statusCode());
        assertEquals(routeOnB, json(response).get("id").asString());
        assertEquals("OPO", json(response).get("originIATA").asString());
        assertTrue(response.headers().firstValue(AisafeHeaders.CORRELATION_ID).isPresent());
    }

    @Test
    @Order(3)
    void searchAggregatesTheRoutesOfAllReplicas() throws Exception {
        for (String url : List.of(urlA, urlB)) {
            HttpResponse<String> response = send("GET", url + "/api/routes/search", null);
            assertEquals(200, response.statusCode());
            assertEquals(List.of(routeOnA, routeOnB).stream().sorted().toList(), ids(json(response)));
            assertTrue(response.headers().firstValue(AisafeHeaders.PARTIAL_RESPONSE).isEmpty());
        }
    }

    @Test
    @Order(4)
    void duplicateRouteIsDetectedAcrossReplicas() throws Exception {
        HttpResponse<String> response = send("POST", urlA + "/api/routes",
                "{\"originIATA\":\"OPO\",\"destIATA\":\"LIS\",\"estimatedFlightTime\":55,\"minRange\":400,\"minCapacity\":120}");

        assertEquals(400, response.statusCode(), response.body());
    }

    @Test
    @Order(5)
    void updateIsForwardedToTheOwnerReplica() throws Exception {
        HttpResponse<String> patched = send("PATCH", urlA + "/api/routes/" + routeOnB, "{\"minCapacity\":130}");
        assertEquals(200, patched.statusCode(), patched.body());
        assertEquals(130, json(patched).get("minCapacity").asInt());

        // A alteração ficou na réplica dona (B)
        assertEquals(130, json(send("GET", urlB + "/api/routes/" + routeOnB, null)).get("minCapacity").asInt());

        // O histórico (guardado em B) também é obtido através de A
        HttpResponse<String> history = send("GET", urlA + "/api/routes/" + routeOnB + "/history", null);
        assertEquals(200, history.statusCode());
        assertEquals(2, json(history).size());

        // Erro de negócio do dono é propagado (rota já ativa → 409)
        assertEquals(409, send("PATCH", urlA + "/api/routes/" + routeOnB, "{\"status\":\"ACTIVE\"}").statusCode());
    }

    @Test
    @Order(6)
    void flightCanBeScheduledOnAReplicaThatDoesNotStoreTheRoute() throws Exception {
        HttpResponse<String> created = send("POST", urlA + "/api/scheduled-flights",
                "{\"aircraftRegistration\":\"CS-TUA\",\"routeID\":\"" + routeOnB + "\",\"date\":\"2026-11-02\",\"time\":\"08:00:00\"}");
        assertEquals(201, created.statusCode(), created.body());
        flightOnA = json(created).get("id").asString();

        // Mesmo avião, mesma hora, noutra réplica → conflito detetado via peers
        HttpResponse<String> conflict = send("POST", urlB + "/api/scheduled-flights",
                "{\"aircraftRegistration\":\"CS-TUA\",\"routeID\":\"" + routeOnA + "\",\"date\":\"2026-11-02\",\"time\":\"08:00:00\"}");
        assertEquals(409, conflict.statusCode(), conflict.body());

        // O voo (guardado em A) é encontrado a partir de B
        HttpResponse<String> fromB = send("GET", urlB + "/api/scheduled-flights/" + flightOnA, null);
        assertEquals(200, fromB.statusCode());
        assertEquals(routeOnB, json(fromB).get("routeID").asString());
    }

    @Test
    @Order(7)
    void popularityAndOperationalHoursAggregateFlightsOfAllReplicas() throws Exception {
        // Rota em B, voo em A: a popularidade vista de B tem de contar o voo de A
        JsonNode active = json(send("GET", urlB + "/api/routes/active?sortBy=popularity", null));
        assertEquals(routeOnB, active.get(0).get("id").asString());
        assertEquals(1, active.get(0).get("usageCount").asLong());

        JsonNode hours = json(send("GET", urlB + "/api/scheduled-flights/operational-hours", null));
        assertEquals("CS-TUA", hours.get("content").get(0).get("registrationNumber").asString());
        assertEquals(55, hours.get("content").get(0).get("totalMinutes").asLong());

        JsonNode distance = json(send("GET", urlA + "/api/routes/network/total-distance", null));
        assertTrue(distance.get("totalDistanceKm").asDouble() > 0);
    }

    @Test
    @Order(8)
    void alternativeRoutesCombineLegsStoredInDifferentReplicas() throws Exception {
        JsonNode itineraries = json(send("GET", urlA + "/api/routes/alternatives?origin=OPO&dest=MAD", null));

        assertEquals(1, itineraries.size());
        assertEquals(1, itineraries.get(0).get("numberOfStops").asInt());
        assertEquals(routeOnB, itineraries.get(0).get("legs").get(0).get("routeID").asString());
        assertEquals(routeOnA, itineraries.get(0).get("legs").get(1).get("routeID").asString());
    }

    @Test
    @Order(9)
    void peerRequestsRequireTheServiceKeyAndAreAnsweredLocally() throws Exception {
        // Sem chave: um cliente não se pode fazer passar por uma réplica
        assertEquals(401, send("GET", urlB + "/api/routes/search", null,
                Map.of(AisafeHeaders.PEER_HOPS, "1")).statusCode());
        // Chave errada
        assertEquals(401, send("GET", urlB + "/api/routes/search", null,
                Map.of(AisafeHeaders.PEER_HOPS, "1", AisafeHeaders.SERVICE_KEY, "wrong")).statusCode());
        // Hops acima do máximo (proteção contra ciclos)
        assertEquals(400, send("GET", urlB + "/api/routes/search", null,
                Map.of(AisafeHeaders.PEER_HOPS, "2", AisafeHeaders.SERVICE_KEY, SERVICE_KEY)).statusCode());

        // Com a chave certa, a réplica responde só com os seus dados (não reencaminha)
        HttpResponse<String> local = send("GET", urlB + "/api/routes/search", null,
                Map.of(AisafeHeaders.PEER_HOPS, "1", AisafeHeaders.SERVICE_KEY, SERVICE_KEY));
        assertEquals(200, local.statusCode());
        assertEquals(List.of(routeOnB), ids(json(local)));
    }

    @Test
    @Order(10)
    void securityAndHealth() throws Exception {
        HttpResponse<String> noToken = HTTP.send(HttpRequest.newBuilder(URI.create(urlA + "/api/routes/search")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noToken.statusCode());

        String backoffice = replicaA.getBean(JwtTokenProvider.class)
                .generateToken("bo-it", List.of("ROLE_BACKOFFICE_OPERATOR"));
        HttpResponse<String> wrongRole = HTTP.send(HttpRequest.newBuilder(URI.create(urlA + "/api/routes/search"))
                .header("Authorization", "Bearer " + backoffice).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, wrongRole.statusCode());

        HttpResponse<String> health = HTTP.send(HttpRequest.newBuilder(URI.create(urlA + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertEquals("UP", json(health).get("status").asString());
        assertTrue(json(health).get("components").has("peers"), health.body());
    }

    @Test
    @Order(11)
    void whenAReplicaFailsTheOthersKeepServingWithPartialData() throws Exception {
        replicaB.close(); // simula a falha da réplica B

        HttpResponse<String> search = send("GET", urlA + "/api/routes/search", null);
        assertEquals(200, search.statusCode());
        assertEquals(List.of(routeOnA), ids(json(search)));
        assertEquals("true", search.headers().firstValue(AisafeHeaders.PARTIAL_RESPONSE).orElse(null));

        // Dados locais continuam acessíveis; dados só existentes em B → 404 (slides, teste 03)
        assertEquals(200, send("GET", urlA + "/api/routes/" + routeOnA, null).statusCode());
        assertEquals(404, send("GET", urlA + "/api/routes/" + routeOnB, null).statusCode());
    }

    // ------------------------------------------------------------------ infraestrutura do teste

    private static ConfigurableApplicationContext startReplica(String name, int port, String peerUrl, String remotes) {
        // Argumentos de linha de comando: têm precedência sobre o application.properties
        return new SpringApplicationBuilder(FlightRoutesApplication.class).run(
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

    /** Stub dos serviços Airports e Aircraft Management (mesmo servidor, caminhos diferentes). */
    private static HttpServer startRemoteServicesStub() throws IOException {
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
            String body = path.startsWith("/airports/") ? airports.get(path.substring("/airports/".length())) : other.get(path);
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
