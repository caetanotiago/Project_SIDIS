package isep.psoft.aisafe.messaging;

import com.sun.net.httpserver.HttpServer;
import isep.psoft.aisafe.FlightRoutesApplication;
import isep.sidis.common.messaging.MessagingNames;
import isep.sidis.common.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End to end over a REAL RabbitMQ: two Flight Routes replicas (A and B, each with its own H2 and its own queue)
 * converge through route events. <b>Skipped</b> (not failed) when no broker is reachable or no credentials are set.
 *
 * <p>Broker: {@code RABBITMQ_HOST}/{@code RABBITMQ_PORT} (default localhost:5672); credentials
 * {@code RABBITMQ_USER}/{@code RABBITMQ_PASSWORD} from the environment or from {@code infra/.env}.
 * Start one with: {@code docker compose -f infra/docker-compose.rabbitmq.yml --env-file infra/.env up -d --wait}.
 * Queues get random instance ids and are deleted at the end.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf(value = "brokerAvailable",
        disabledReason = "RabbitMQ not reachable or RABBITMQ_USER/RABBITMQ_PASSWORD not set (see infra/.env.example)")
class RouteEventsBrokerTest {

    private static final String SERVICE_KEY = "it-service-key";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Duration CONVERGENCE_TIMEOUT = Duration.ofSeconds(15);

    private static HttpServer airports;
    private static ConfigurableApplicationContext replicaA;
    private static ConfigurableApplicationContext replicaB;
    private static String idA;
    private static String idB;
    private static String urlA;
    private static String urlB;
    private static String atcc;
    private static String routeId;

    @BeforeAll
    static void start() throws IOException {
        Map<String, String> broker = brokerSettings();
        Assumptions.assumeTrue(broker != null, "broker went away after the class was enabled");

        airports = startAirportsStub();
        String airportsUrl = "http://localhost:" + airports.getAddress().getPort();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        idA = "it-a-" + suffix;
        idB = "it-b-" + suffix;
        int portA = freePort();
        int portB = freePort();
        urlA = "http://localhost:" + portA;
        urlB = "http://localhost:" + portB;

        replicaA = startReplica(idA, portA, urlB, airportsUrl, broker);
        replicaB = startReplica(idB, portB, urlA, airportsUrl, broker);
        atcc = replicaA.getBean(JwtTokenProvider.class).generateToken("atcc-it", List.of("ROLE_ATCC"));
    }

    @AfterAll
    static void stop() {
        for (var replica : new ConfigurableApplicationContext[] { replicaA, replicaB }) {
            if (replica == null) continue;
            String id = replica == replicaA ? idA : idB;
            String queue = MessagingNames.consumerQueue("flightroutes", id, "flightroutes");
            AmqpAdmin admin = replica.getBean(AmqpAdmin.class);
            replica.close();
            admin.deleteQueue(queue);
            admin.deleteQueue(MessagingNames.deadLetterQueue(queue));
        }
        if (airports != null) airports.stop(0);
    }

    @Test
    @Order(1)
    void aRouteCreatedOnReplicaAReachesTheReadModelOfBothReplicas() throws Exception {
        HttpResponse<String> created = send("POST", urlA + "/api/routes", atcc,
                "{\"originIATA\":\"OPO\",\"destIATA\":\"LIS\",\"estimatedFlightTime\":55,\"minRange\":400,\"minCapacity\":120}");
        assertEquals(201, created.statusCode(), created.body());
        routeId = JSON.readTree(created.body()).get("id").asString();

        for (String url : List.of(urlA, urlB)) {
            JsonNode replicated = awaitReplicated(url, routeId, r -> true);
            assertEquals("OPO", replicated.get("originIATA").asString());
            assertEquals(idA, replicated.get("ownerInstance").asString(), "the owner is the replica that stored it");
            assertEquals(0, replicated.get("version").asLong());
        }
    }

    @Test
    @Order(2)
    void anUpdateSentToReplicaBIsExecutedByTheOwnerAndPropagatedToBoth() throws Exception {
        HttpResponse<String> patched = send("PATCH", urlB + "/api/routes/" + routeId, atcc, "{\"minCapacity\":150}");
        assertEquals(200, patched.statusCode(), patched.body());

        for (String url : List.of(urlA, urlB)) {
            JsonNode replicated = awaitReplicated(url, routeId, r -> r.get("minCapacity").asInt() == 150);
            assertEquals(1, replicated.get("version").asLong());
        }
    }

    @Test
    @Order(3)
    void theWriteModelIsStillPartitionedSoSummedStatisticsAreNotDoubled() throws Exception {
        String backoffice = replicaA.getBean(JwtTokenProvider.class).generateToken("bo-it", List.of("ROLE_BACKOFFICE_OPERATOR"));
        HttpResponse<String> counts = HTTP.send(HttpRequest.newBuilder(URI.create(urlB + "/api/routes/statistics/count-by-airport"))
                .header("Authorization", "Bearer " + backoffice).header("X-Service-Key", SERVICE_KEY).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, counts.statusCode(), counts.body());
        assertEquals(1, JSON.readTree(counts.body()).get("OPO").asLong());
        assertEquals(1, JSON.readTree(counts.body()).get("LIS").asLong());
    }

    @Test
    @Order(4)
    void aMessageThatCannotBeProcessedEndsInTheDeadLetterQueueOfEachReplica() throws Exception {
        String broken = """
                {"eventId":"%s","eventType":"RouteCreated","timestamp":"%s","sourceService":"flightroutes",
                 "sourceInstance":"it-poison","aggregateId":"none","payload":{"originIata":"OPO"}}"""
                .formatted(UUID.randomUUID(), Instant.now());
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        replicaA.getBean(RabbitTemplate.class).send(MessagingNames.eventsExchange("flightroutes"), "route.created",
                new Message(broken.getBytes(StandardCharsets.UTF_8), props));

        AmqpAdmin admin = replicaA.getBean(AmqpAdmin.class);
        for (String id : List.of(idA, idB)) {
            String dlq = MessagingNames.deadLetterQueue(MessagingNames.consumerQueue("flightroutes", id, "flightroutes"));
            Instant deadline = Instant.now().plus(CONVERGENCE_TIMEOUT);
            long count = 0;
            while (Instant.now().isBefore(deadline)) {
                QueueInformation info = admin.getQueueInfo(dlq);
                count = info == null ? 0 : info.getMessageCount();
                if (count > 0) break;
                Thread.sleep(250);
            }
            assertEquals(1, count, "poison message must be dead-lettered to " + dlq);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static JsonNode awaitReplicated(String url, String id, Predicate<JsonNode> condition) throws Exception {
        Instant deadline = Instant.now().plus(CONVERGENCE_TIMEOUT);
        String last = "";
        while (Instant.now().isBefore(deadline)) {
            HttpResponse<String> r = send("GET", url + "/api/routes/replicated/" + id, atcc, null);
            last = r.statusCode() + " " + r.body();
            if (r.statusCode() == 200) {
                JsonNode body = JSON.readTree(r.body());
                if (condition.test(body)) return body;
            }
            Thread.sleep(200);
        }
        return fail("replica " + url + " did not converge within " + CONVERGENCE_TIMEOUT + ": " + last);
    }

    private static ConfigurableApplicationContext startReplica(String id, int port, String peer, String airportsUrl,
                                                               Map<String, String> broker) {
        return new SpringApplicationBuilder(FlightRoutesApplication.class).run(
                "--server.port=" + port,
                "--aisafe.instance-id=" + id,
                "--spring.datasource.url=jdbc:h2:mem:" + id + ";DB_CLOSE_DELAY=-1",
                "--aisafe.peers.urls=" + peer,
                "--services.airports.urls=" + airportsUrl,
                "--services.aircraft.urls=" + airportsUrl,
                "--aisafe.security.service-key=" + SERVICE_KEY,
                "--aisafe.bootstrap.enabled=false",
                "--aisafe.messaging.enabled=true",
                "--aisafe.messaging.initial-interval=100ms",
                "--aisafe.messaging.max-interval=200ms",
                "--spring.rabbitmq.host=" + broker.get("host"),
                "--spring.rabbitmq.port=" + broker.get("port"),
                "--spring.rabbitmq.username=" + broker.get("user"),
                "--spring.rabbitmq.password=" + broker.get("password"),
                "--logging.file.name=target/it-logs/broker-" + id + ".log");
    }

    static boolean brokerAvailable() {
        return brokerSettings() != null;
    }

    /** Broker address and credentials, or {@code null} if the broker is not usable (the test is then skipped). */
    private static Map<String, String> brokerSettings() {
        Map<String, String> env = new HashMap<>(System.getenv());
        Path dotEnv = Path.of("..", "..", "infra", ".env"); // surefire runs in code/flightroutes
        if (Files.exists(dotEnv)) {
            try {
                for (String line : Files.readAllLines(dotEnv)) {
                    String t = line.trim();
                    int eq = t.indexOf('=');
                    if (!t.startsWith("#") && eq > 0) env.putIfAbsent(t.substring(0, eq).trim(), t.substring(eq + 1).trim());
                }
            } catch (IOException ignored) {
                // fall back to the environment only
            }
        }
        String host = env.getOrDefault("RABBITMQ_HOST", "localhost");
        int port = Integer.parseInt(env.getOrDefault("RABBITMQ_PORT", "5672"));
        String user = env.get("RABBITMQ_USER");
        String password = env.get("RABBITMQ_PASSWORD");
        if (user == null || user.isBlank() || password == null || password.isBlank()) return null;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
        } catch (IOException e) {
            return null;
        }
        return Map.of("host", host, "port", String.valueOf(port), "user", user, "password", password);
    }

    private static HttpServer startAirportsStub() throws IOException {
        Map<String, String> airports = Map.of(
                "OPO", "{\"iataCode\":\"OPO\",\"latitude\":41.2481,\"longitude\":-8.6814,\"status\":\"OPERATIONAL\"}",
                "LIS", "{\"iataCode\":\"LIS\",\"latitude\":38.7742,\"longitude\":-9.1342,\"status\":\"OPERATIONAL\"}");
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/airports/", exchange -> {
            String iata = exchange.getRequestURI().getPath().substring("/api/airports/".length());
            String body = airports.get(iata);
            byte[] bytes = (body == null ? "{\"error\":\"not found\"}" : body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(body == null ? 404 : 200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return server;
    }

    private static HttpResponse<String> send(String method, String url, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + token);
        if (body != null) b.header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
