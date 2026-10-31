package isep.psoft.aisafe.distributed;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import isep.psoft.aisafe.MaintenanceApplication;
import isep.psoft.aisafe.maintenance.bootstrap.ReferenceTemplatesBootstrap;
import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Colaboração entre réplicas da Maintenance: DUAS réplicas reais (A e B, cada uma com a sua H2),
 * peers uma da outra, e um stub com estado do Aircraft Management (aeronaves, modelos, PATCH do
 * estado, frota e horas de voo). Cobre os cenários S1–S6 de docs/5-scenarios/maintenance.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MaintenanceReplicaCollaborationTest {

    private static final String SERVICE_KEY = "it-service-key";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String A_CHECK_ID = MaintenanceTemplate.referenceId(ReferenceTemplatesBootstrap.A_CHECK);

    /** Estado das aeronaves no stub do Aircraft Management (matrícula → estado). */
    private static final Map<String, String> AIRCRAFT_STATUS = new ConcurrentHashMap<>();

    private static HttpServer aircraftManagement;
    private static ConfigurableApplicationContext replicaA;
    private static ConfigurableApplicationContext replicaB;
    private static String urlA;
    private static String urlB;
    private static String tech;
    private static String supervisor;
    private static String atcc;

    private static String templateOnB;   // template criado em B (só A320, de 30 em 30 dias)
    private static String recordOnA;     // CS-TUA, A-Check, guardado em A
    private static String recordOnB;     // CS-TUA, template de B, guardado em B

    @BeforeAll
    static void startReplicas() throws IOException {
        AIRCRAFT_STATUS.put("CS-TUA", "AVAILABLE");
        AIRCRAFT_STATUS.put("CS-TUE", "AVAILABLE");
        AIRCRAFT_STATUS.put("CS-TUF", "IN_FLIGHT");
        aircraftManagement = startAircraftManagementStub();
        String am = "http://localhost:" + aircraftManagement.getAddress().getPort();

        int portA = freePort();
        int portB = freePort();
        urlA = "http://localhost:" + portA;
        urlB = "http://localhost:" + portB;
        replicaA = startReplica("A", portA, urlB, am);
        replicaB = startReplica("B", portB, urlA, am);

        JwtTokenProvider jwt = replicaA.getBean(JwtTokenProvider.class);
        tech = jwt.generateToken("tech-it", List.of("ROLE_MAINTENANCE_TECHNICIAN"));
        supervisor = jwt.generateToken("sup-it", List.of("ROLE_MAINTENANCE_SUPERVISOR"));
        atcc = jwt.generateToken("atcc-it", List.of("ROLE_ATCC"));
    }

    @AfterAll
    static void stopAll() {
        if (replicaA != null) replicaA.close();
        if (replicaB != null && replicaB.isActive()) replicaB.close();
        if (aircraftManagement != null) aircraftManagement.stop(0);
    }

    // ------------------------------------------------------------------ templates (US115)

    @Test
    @Order(1)
    void templatesAreCreatedInOneReplicaAndFoundFromAnyReplica() throws Exception {
        // Templates de referência (WP#0A): em todas as réplicas, sem duplicados na listagem
        JsonNode list = json(send("GET", urlA + "/api/maintenance-templates", null, tech));
        assertEquals(4, list.size());

        HttpResponse<String> created = send("POST", urlB + "/api/maintenance-templates",
                "{\"name\":\"Avionics Software Update\",\"type\":\"modification\",\"applicableModels\":[\"A320\"],"
                        + "\"checklist\":[\"Load software\",\"Run self-test\"],\"calendarDaysInterval\":30}", tech);
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(created.headers().firstValue("Location").isPresent());
        templateOnB = json(created).get("id").asString();

        // GET por id em A de um template guardado em B (S1)
        HttpResponse<String> fromA = send("GET", urlA + "/api/maintenance-templates/" + templateOnB, null, tech);
        assertEquals(200, fromA.statusCode());
        assertEquals("MODIFICATION", json(fromA).get("type").asString());
        assertEquals(5, json(send("GET", urlA + "/api/maintenance-templates", null, tech)).size());

        // Nome repetido (existe em B) e modelo desconhecido (B3c) → 400
        assertEquals(400, send("POST", urlA + "/api/maintenance-templates",
                "{\"name\":\"avionics software update\",\"type\":\"INSPECTION\",\"applicableModels\":[\"A320\"],\"checklist\":[\"x\"]}",
                tech).statusCode());
        assertEquals(400, send("POST", urlA + "/api/maintenance-templates",
                "{\"name\":\"Other\",\"type\":\"INSPECTION\",\"applicableModels\":[\"X999\"],\"checklist\":[\"x\"]}",
                tech).statusCode());
    }

    // ------------------------------------------------------------------ registos (US115)

    @Test
    @Order(2)
    void recordsAreStoredWhereTheyAreCreatedAndTheAircraftGoesUnderMaintenance() throws Exception {
        // Registo em A com um template de referência
        HttpResponse<String> onA = send("POST", urlA + "/api/maintenance-records", record("CS-TUA", A_CHECK_ID, null), tech);
        assertEquals(201, onA.statusCode(), onA.body());
        recordOnA = json(onA).get("id").asString();
        assertEquals("IN_PROGRESS", json(onA).get("status").asString());
        assertEquals(4, json(onA).get("checklist").size());
        assertEquals("UNDER_MAINTENANCE", AIRCRAFT_STATUS.get("CS-TUA"));

        // 2.º registo em A (template guardado em B, encontrado pelos peers) e um registo em B
        HttpResponse<String> secondOnA = send("POST", urlA + "/api/maintenance-records", record("cs-tua", templateOnB, "avionics"), tech);
        assertEquals(201, secondOnA.statusCode(), secondOnA.body());
        recordOnB = json(send("POST", urlB + "/api/maintenance-records", record("CS-TUA", templateOnB, null), tech)).get("id").asString();
        assertEquals(200, send("GET", urlB + "/api/maintenance-records/" + recordOnB, null, tech).statusCode());

        // Aeronave em voo → 409 do Aircraft Management, nada gravado
        assertEquals(409, send("POST", urlA + "/api/maintenance-records", record("CS-TUF", A_CHECK_ID, null), tech).statusCode());
        // Aeronave desconhecida → 404; template não aplicável ao modelo (E190) → 400; data futura → 400
        assertEquals(404, send("POST", urlA + "/api/maintenance-records", record("CS-XXX", A_CHECK_ID, null), tech).statusCode());
        assertEquals(400, send("POST", urlA + "/api/maintenance-records", record("CS-TUE", templateOnB, null), tech).statusCode());
        assertEquals(400, send("POST", urlA + "/api/maintenance-records",
                "{\"aircraftRegistration\":\"CS-TUA\",\"templateId\":\"" + A_CHECK_ID + "\",\"description\":\"x\","
                        + "\"startDate\":\"" + LocalDate.now().plusDays(3) + "\",\"expectedDurationMinutes\":60}", tech).statusCode());
        assertEquals("IN_FLIGHT", AIRCRAFT_STATUS.get("CS-TUF"));
    }

    @Test
    @Order(3)
    void getByIdIsForwardedAndCollectionsAreAggregated() throws Exception {
        HttpResponse<String> forwarded = send("GET", urlA + "/api/maintenance-records/" + recordOnB, null, tech);
        assertEquals(200, forwarded.statusCode());
        assertEquals(recordOnB, json(forwarded).get("id").asString());
        assertTrue(json(forwarded).get("_links").get("self").get("href").asString().startsWith(urlA));
        assertEquals(404, send("GET", urlA + "/api/maintenance-records/missing-id", null, tech).statusCode());

        // US219 a partir de A e de B → a mesma lista (3 em curso: 2 em A, 1 em B)
        List<String> fromA = ids(json(send("GET", urlA + "/api/maintenance-records/ongoing", null, supervisor)).get("content"));
        List<String> fromB = ids(json(send("GET", urlB + "/api/maintenance-records/ongoing", null, supervisor)).get("content"));
        assertEquals(3, fromA.size());
        assertEquals(fromA, fromB);

        // US116: aeronave existente (3 registos) / inexistente → 404; paginação depois de juntar
        JsonNode page = json(send("GET", urlB + "/api/maintenance-records/aircraft/CS-TUA?page=0&size=2", null, tech));
        assertEquals(3, page.get("totalElements").asInt());
        assertEquals(2, page.get("content").size());
        assertEquals(2, page.get("totalPages").asInt());
        assertEquals(404, send("GET", urlB + "/api/maintenance-records/aircraft/CS-XXX", null, tech).statusCode());

        // US218: filtro por componente e por intervalo de datas
        assertEquals(1, json(send("GET", urlB + "/api/maintenance-records?component=AVIONICS", null, tech)).get("totalElements").asInt());
        assertEquals(0, json(send("GET", urlB + "/api/maintenance-records?to=" + LocalDate.now().minusDays(1), null, tech))
                .get("totalElements").asInt());
        assertEquals(400, send("GET", urlB + "/api/maintenance-records?from=2026-12-01&to=2026-01-01", null, tech).statusCode());
    }

    // ------------------------------------------------------------------ comandos (US217, US119)

    @Test
    @Order(4)
    void commandsAreExecutedByTheOwnerReplica() throws Exception {
        HttpResponse<String> categorized = send("PATCH", urlA + "/api/maintenance-records/" + recordOnB + "/component",
                "{\"component\":\"engine\"}", tech);
        assertEquals(200, categorized.statusCode(), categorized.body());
        assertEquals("ENGINE", json(send("GET", urlB + "/api/maintenance-records/" + recordOnB, null, tech)).get("component").asString());

        // Completar em A um registo de B: executado por B; ainda há registos em curso → aeronave não é libertada
        HttpResponse<String> completed = send("PATCH", urlA + "/api/maintenance-records/" + recordOnB + "/complete",
                "{\"notes\":\"Software updated\",\"cost\":1200.50,\"actualDurationMinutes\":90,\"aircraftFlightHours\":1500}", tech);
        assertEquals(200, completed.statusCode(), completed.body());
        assertEquals("COMPLETED", json(completed).get("status").asString());
        assertEquals("false", completed.headers().firstValue("X-Aircraft-Released").orElse(null));
        assertEquals("UNDER_MAINTENANCE", AIRCRAFT_STATUS.get("CS-TUA"));

        // Erros de negócio do dono propagados: já concluído → 409; recategorizar concluído → 409
        assertEquals(409, send("PATCH", urlA + "/api/maintenance-records/" + recordOnB + "/complete",
                "{\"notes\":\"again\",\"cost\":1,\"actualDurationMinutes\":1}", tech).statusCode());
        assertEquals(409, send("PATCH", urlA + "/api/maintenance-records/" + recordOnB + "/component",
                "{\"component\":\"ENGINE\"}", tech).statusCode());
        // Validação → 400
        assertEquals(400, send("PATCH", urlA + "/api/maintenance-records/" + recordOnA + "/complete",
                "{\"notes\":\"x\",\"cost\":-1,\"actualDurationMinutes\":10}", tech).statusCode());
    }

    @Test
    @Order(5)
    void theAircraftIsReleasedWhenItsLastOngoingRecordIsCompleted() throws Exception {
        List<String> ongoing = ids(json(send("GET", urlA + "/api/maintenance-records/ongoing", null, supervisor)).get("content"));
        assertEquals(2, ongoing.size());

        String first = ongoing.get(0);
        String second = ongoing.get(1);
        HttpResponse<String> r1 = send("PATCH", urlB + "/api/maintenance-records/" + first + "/complete",
                "{\"notes\":\"done\",\"cost\":5000,\"actualDurationMinutes\":600,\"aircraftFlightHours\":1500}", tech);
        assertEquals("false", r1.headers().firstValue("X-Aircraft-Released").orElse(null));
        HttpResponse<String> r2 = send("PATCH", urlB + "/api/maintenance-records/" + second + "/complete",
                "{\"notes\":\"done\",\"cost\":800,\"actualDurationMinutes\":300,\"aircraftFlightHours\":1500}", tech);
        assertEquals(200, r2.statusCode(), r2.body());
        assertEquals("true", r2.headers().firstValue("X-Aircraft-Released").orElse(null));
        assertEquals("AVAILABLE", AIRCRAFT_STATUS.get("CS-TUA"));
    }

    // ------------------------------------------------------------------ estatísticas e alertas

    @Test
    @Order(6)
    void statisticsAddThePartialResultsOfEveryReplica() throws Exception {
        JsonNode hours = json(send("GET", urlB + "/api/maintenance-records/statistics/hours", null, atcc));
        assertEquals(90 + 600 + 300, hours.get("totalMinutes").asLong());
        assertEquals(16.5, hours.get("totalHours").asDouble());

        JsonNode costs = json(send("GET", urlA + "/api/maintenance-records/reports/costs?groupBy=MODEL", null, atcc));
        assertEquals(7000.50, costs.get("totalCost").asDouble());
        assertEquals("A320", costs.get("items").get(0).get("key").asString());
        assertEquals(400, send("GET", urlA + "/api/maintenance-records/reports/costs?groupBy=COLOR", null, atcc).statusCode());

        JsonNode turnaround = json(send("GET", urlB + "/api/maintenance-records/reports/turnaround", null, supervisor));
        assertEquals(3, turnaround.get("items").get(0).get("completedRecords").asLong());
        assertEquals(330.0, turnaround.get("items").get(0).get("averageMinutes").asDouble());
    }

    @Test
    @Order(7)
    void alertsUseTheFleetFlightHoursTemplatesAndCompletionsOfAllReplicas() throws Exception {
        JsonNode alerts = json(send("GET", urlA + "/api/maintenance-alerts", null, atcc));
        List<String> keys = new ArrayList<>();
        alerts.forEach(a -> keys.add(a.get("aircraftRegistration").asString() + "|" + a.get("templateName").asString()
                + "|" + a.get("trigger").asString()));

        // CS-TUA (2019): C-Check nunca feito → dias; A-Check feito hoje com 1500 h (agora 1600) → não
        assertTrue(keys.contains("CS-TUA|C-Check Heavy Maintenance|CALENDAR_DAYS"), keys.toString());
        assertFalse(keys.stream().anyMatch(k -> k.startsWith("CS-TUA|A-Check")), keys.toString());
        assertFalse(keys.stream().anyMatch(k -> k.startsWith("CS-TUA|Avionics")), keys.toString());
        // CS-TUE (fabricado há 200 dias): A-Check (120 dias) em atraso; C-Check (730) não
        assertTrue(keys.contains("CS-TUE|A-Check Inspection|CALENDAR_DAYS"), keys.toString());
        assertFalse(keys.contains("CS-TUE|C-Check Heavy Maintenance|CALENDAR_DAYS"), keys.toString());
        // CS-TUG está INACTIVE → ignorada
        assertFalse(keys.stream().anyMatch(k -> k.startsWith("CS-TUG")), keys.toString());
    }

    // ------------------------------------------------------------------ segurança

    @Test
    @Order(8)
    void securityRulesAndPeerAuthentication() throws Exception {
        String uri = "/api/maintenance-records/search";
        assertEquals(401, send("GET", urlB + uri, null, tech, Map.of(AisafeHeaders.PEER_HOPS, "1")).statusCode());
        assertEquals(401, send("GET", urlB + uri, null, tech,
                Map.of(AisafeHeaders.PEER_HOPS, "1", AisafeHeaders.SERVICE_KEY, "wrong")).statusCode());
        assertEquals(400, send("GET", urlB + uri, null, tech,
                Map.of(AisafeHeaders.PEER_HOPS, "2", AisafeHeaders.SERVICE_KEY, SERVICE_KEY)).statusCode());
        HttpResponse<String> local = send("GET", urlB + uri, null, tech,
                Map.of(AisafeHeaders.PEER_HOPS, "1", AisafeHeaders.SERVICE_KEY, SERVICE_KEY));
        assertEquals(200, local.statusCode());
        assertEquals(List.of(recordOnB), ids(json(local)));

        HttpResponse<String> noToken = HTTP.send(HttpRequest.newBuilder(URI.create(urlA + uri)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noToken.statusCode());
        assertEquals(403, send("POST", urlA + "/api/maintenance-records", record("CS-TUA", A_CHECK_ID, null), atcc).statusCode());
        assertEquals(403, send("GET", urlA + "/api/maintenance-alerts", null, tech).statusCode());
        assertEquals(403, send("GET", urlA + "/api/maintenance-records/ongoing", null, tech).statusCode());
    }

    // ------------------------------------------------------------------ falha de uma réplica

    @Test
    @Order(9)
    void whenAReplicaFailsTheOthersKeepServingSafely() throws Exception {
        replicaB.close();

        HttpResponse<String> search = send("GET", urlA + "/api/maintenance-records/aircraft/CS-TUA", null, tech);
        assertEquals(200, search.statusCode());
        assertEquals("true", search.headers().firstValue(AisafeHeaders.PARTIAL_RESPONSE).orElse(null));
        assertEquals(404, send("GET", urlA + "/api/maintenance-records/" + recordOnB, null, tech).statusCode());
        assertEquals(200, send("GET", urlA + "/api/maintenance-records/" + recordOnA, null, tech).statusCode());

        // Novo registo em A e conclusão: B não respondeu → a aeronave fica UNDER_MAINTENANCE (lado seguro)
        String id = json(send("POST", urlA + "/api/maintenance-records", record("CS-TUE", A_CHECK_ID, null), tech)).get("id").asString();
        HttpResponse<String> completed = send("PATCH", urlA + "/api/maintenance-records/" + id + "/complete",
                "{\"notes\":\"done\",\"cost\":100,\"actualDurationMinutes\":60}", tech);
        assertEquals(200, completed.statusCode(), completed.body());
        assertEquals("false", completed.headers().firstValue("X-Aircraft-Released").orElse(null));
        assertEquals("UNDER_MAINTENANCE", AIRCRAFT_STATUS.get("CS-TUE"));
    }

    // ------------------------------------------------------------------ infraestrutura do teste

    private static String record(String registration, String templateId, String component) {
        return "{\"aircraftRegistration\":\"" + registration + "\",\"templateId\":\"" + templateId + "\","
                + "\"description\":\"IT record\",\"startDate\":\"" + LocalDate.now() + "\",\"expectedDurationMinutes\":120"
                + (component == null ? "" : ",\"component\":\"" + component + "\"") + "}";
    }

    private static ConfigurableApplicationContext startReplica(String name, int port, String peerUrl, String am) {
        return new SpringApplicationBuilder(MaintenanceApplication.class).run(
                "--server.port=" + port,
                "--aisafe.instance-id=it-maintenance-" + name,
                "--spring.datasource.url=jdbc:h2:mem:it-maintenance-" + name + ";DB_CLOSE_DELAY=-1",
                "--aisafe.peers.urls=" + peerUrl,
                "--services.aircraft.urls=" + am,
                "--aisafe.security.service-key=" + SERVICE_KEY,
                "--aisafe.http.read-timeout=2s",
                "--aisafe.http.max-retries=0",
                "--maintenance.demo.enabled=false",
                "--logging.file.name=target/it-logs/maintenance-" + name + ".log");
    }

    /** Stub com estado do Aircraft Management (contratos B3a–B3d). */
    private static HttpServer startAircraftManagementStub() throws IOException {
        LocalDate now = LocalDate.now();
        Map<String, String[]> aircraft = Map.of( // matrícula → modelo, data de fabrico, horas de voo
                "CS-TUA", new String[]{"A320", "2019-05-14", "1600"},
                "CS-TUE", new String[]{"E190", now.minusDays(200).toString(), "100"},
                "CS-TUF", new String[]{"E190", now.minusDays(10).toString(), "5"},
                "CS-TUG", new String[]{"E190", "2015-01-01", "9000"});
        AIRCRAFT_STATUS.put("CS-TUG", "INACTIVE");

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/api/aircrafts/operational-hours")) {
                List<String> rows = new ArrayList<>();
                aircraft.forEach((reg, a) -> rows.add("{\"registrationNumber\":\"" + reg + "\",\"modelName\":\"" + a[0]
                        + "\",\"totalMinutes\":0,\"totalHours\":" + a[2] + "}"));
                respond(exchange, 200, page(rows));
            } else if (path.equals("/api/aircrafts")) {
                List<String> rows = new ArrayList<>();
                aircraft.keySet().stream().sorted().forEach(reg -> rows.add(aircraftJson(reg, aircraft.get(reg))));
                respond(exchange, 200, page(rows));
            } else if (path.startsWith("/api/aircraft-models/")) {
                String model = path.substring("/api/aircraft-models/".length());
                boolean exists = List.of("A320", "E190").contains(model);
                respond(exchange, exists ? 200 : 404, exists ? "{\"modelName\":\"" + model + "\",\"maximumRange\":6100}" : "{\"error\":\"not found\"}");
            } else if (path.startsWith("/api/aircrafts/")) {
                String rest = path.substring("/api/aircrafts/".length());
                String reg = rest.endsWith("/status") ? rest.substring(0, rest.length() - "/status".length()) : rest;
                String[] a = aircraft.get(reg);
                if (a == null) {
                    respond(exchange, 404, "{\"error\":\"Aircraft not found: " + reg + "\"}");
                } else if ("PATCH".equals(method)) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String target = JSON.readTree(body).get("status").asString();
                    if ("IN_FLIGHT".equals(AIRCRAFT_STATUS.get(reg)) && "UNDER_MAINTENANCE".equals(target)) {
                        respond(exchange, 409, "{\"error\":\"Aircraft " + reg + " is in flight and cannot change to " + target + "\"}");
                    } else {
                        AIRCRAFT_STATUS.put(reg, target);
                        respond(exchange, 200, aircraftJson(reg, a));
                    }
                } else {
                    respond(exchange, 200, aircraftJson(reg, a));
                }
            } else {
                respond(exchange, 404, "{\"error\":\"not found\"}");
            }
        });
        server.start();
        return server;
    }

    private static String aircraftJson(String reg, String[] a) {
        return "{\"registrationNumber\":\"" + reg + "\",\"modelName\":\"" + a[0] + "\",\"seatingCapacity\":180,"
                + "\"manufacturingDate\":\"" + a[1] + "\",\"status\":\"" + AIRCRAFT_STATUS.get(reg) + "\"}";
    }

    private static String page(List<String> rows) {
        return "{\"content\":[" + String.join(",", rows) + "],\"page\":0,\"size\":100,\"totalElements\":"
                + rows.size() + ",\"totalPages\":1}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
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

    private static List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(n -> ids.add(n.get("id").asString()));
        return ids;
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
