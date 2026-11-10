package isep.psoft.aisafe.messaging;

import isep.psoft.aisafe.FlightRoutesApplication;
import isep.psoft.aisafe.flightroutes.command.RouteIntegrationEventPublisher;
import isep.psoft.aisafe.flightroutes.events.RouteEventPayload;
import isep.psoft.aisafe.flightroutes.events.RouteEvents;
import isep.psoft.aisafe.flightroutes.query.RouteEventsListener;
import isep.psoft.aisafe.flightroutes.query.RouteProjection;
import isep.psoft.aisafe.infrastructure.messaging.ProcessedEventRepository;
import isep.sidis.common.messaging.EventPublisher;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The read model built from route events, in one real replica with its own H2 database, WITHOUT a broker:
 * events are handed to {@link RouteProjection} directly, exactly as {@link RouteEventsListener} does.
 * Also checks that with messaging off (the default in tests) no AMQP bean is active.
 */
class RouteProjectionTest {

    private static final String SELF = "it-projection";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static ConfigurableApplicationContext replica;
    private static RouteProjection projection;
    private static String url;
    private static String token;

    @BeforeAll
    static void start() throws IOException {
        int port = freePort();
        url = "http://localhost:" + port;
        String nobody = "http://localhost:" + freePort();
        replica = new SpringApplicationBuilder(FlightRoutesApplication.class).run(
                "--server.port=" + port,
                "--aisafe.instance-id=" + SELF,
                "--spring.datasource.url=jdbc:h2:mem:it-projection;DB_CLOSE_DELAY=-1",
                "--aisafe.peers.urls=",
                "--services.aircraft.urls=" + nobody,
                "--services.airports.urls=" + nobody,
                "--aisafe.bootstrap.enabled=false",
                "--logging.file.name=target/it-logs/projection.log");
        projection = replica.getBean(RouteProjection.class);
        token = replica.getBean(JwtTokenProvider.class).generateToken("atcc-it", List.of("ROLE_ATCC"));
    }

    @AfterAll
    static void stop() {
        if (replica != null) replica.close();
    }

    private static IntegrationEvent<RouteEventPayload> event(String type, String source, RouteEventPayload p) {
        return IntegrationEvent.create(type, Instant.now(), "flightroutes", source, p.routeId(), p);
    }

    private static RouteEventPayload route(String id, int minCapacity, String status, long version) {
        return new RouteEventPayload(id, "OPO", "LIS", 274.0, 400.0, minCapacity, 55, status, version);
    }

    @Test
    void messagingIsOffByDefaultInTestsSoNoAmqpBeanIsActive() {
        assertTrue(replica.getBeansOfType(EventPublisher.class).isEmpty());
        assertTrue(replica.getBeansOfType(RouteIntegrationEventPublisher.class).isEmpty());
        assertTrue(replica.getBeansOfType(RouteEventsListener.class).isEmpty());
    }

    @Test
    void createdThenUpdatedEventsAreAppliedAndVisibleThroughTheReplicatedEndpoint() throws Exception {
        String id = UUID.randomUUID().toString();

        assertTrue(projection.apply(event(RouteEvents.ROUTE_CREATED, "flightroutes-2", route(id, 120, "ACTIVE", 0))));
        assertTrue(projection.apply(event(RouteEvents.ROUTE_UPDATED, "flightroutes-2", route(id, 150, "INACTIVE", 1))));

        JsonNode body = get("/api/routes/replicated/" + id, 200);
        assertEquals("OPO", body.get("originIATA").asString());
        assertEquals(150, body.get("minCapacity").asInt());
        assertEquals("INACTIVE", body.get("status").asString());
        assertEquals(1, body.get("version").asLong());
        assertEquals("flightroutes-2", body.get("ownerInstance").asString());

        JsonNode all = get("/api/routes/replicated", 200);
        assertTrue(all.isArray());
        assertTrue(all.valueStream().anyMatch(r -> id.equals(r.get("id").asString())));
    }

    @Test
    void aRedeliveredEventIsAppliedOnlyOnceAndItsIdIsStoredInTheReplicaDatabase() throws Exception {
        String id = UUID.randomUUID().toString();
        IntegrationEvent<RouteEventPayload> created = event(RouteEvents.ROUTE_CREATED, "flightroutes-3", route(id, 120, "ACTIVE", 0));

        assertTrue(projection.apply(created));
        assertFalse(projection.apply(created), "same eventId: must be ignored");

        assertTrue(replica.getBean(ProcessedEventRepository.class).existsById(created.eventId().toString()));
        assertEquals(created.eventId().toString(), get("/api/routes/replicated/" + id, 200).get("lastEventId").asString());
    }

    @Test
    void anEventOlderThanTheStoredVersionIsIgnored() throws Exception {
        String id = UUID.randomUUID().toString();
        projection.apply(event(RouteEvents.ROUTE_CREATED, "flightroutes-2", route(id, 120, "ACTIVE", 0)));
        projection.apply(event(RouteEvents.ROUTE_UPDATED, "flightroutes-2", route(id, 200, "ACTIVE", 2)));

        projection.apply(event(RouteEvents.ROUTE_UPDATED, "flightroutes-2", route(id, 130, "ACTIVE", 1)));

        JsonNode body = get("/api/routes/replicated/" + id, 200);
        assertEquals(200, body.get("minCapacity").asInt());
        assertEquals(2, body.get("version").asLong());
    }

    @Test
    void ownEventsAreAppliedLikeAnyOtherSoTheReadModelAlsoHoldsThisReplicasRoutes() throws Exception {
        String id = UUID.randomUUID().toString();

        assertTrue(projection.apply(event(RouteEvents.ROUTE_CREATED, SELF, route(id, 120, "ACTIVE", 0))));

        assertEquals(SELF, get("/api/routes/replicated/" + id, 200).get("ownerInstance").asString());
    }

    @Test
    void deletedRemovesTheRouteAndUnknownEventTypesAreIgnored() throws Exception {
        String id = UUID.randomUUID().toString();
        projection.apply(event(RouteEvents.ROUTE_CREATED, "flightroutes-2", route(id, 120, "ACTIVE", 0)));

        assertTrue(projection.apply(event("RouteRenamed", "flightroutes-2", route(id, 999, "ACTIVE", 5))));
        assertEquals(120, get("/api/routes/replicated/" + id, 200).get("minCapacity").asInt());

        projection.apply(event(RouteEvents.ROUTE_DELETED, "flightroutes-2", RouteEventPayload.deleted(id, 1L)));
        get("/api/routes/replicated/" + id, 404);
    }

    @Test
    void anEventWithoutRouteIdFailsSoTheContainerRetriesAndDeadLettersIt() {
        RouteEventPayload broken = new RouteEventPayload(null, "OPO", "LIS", 1.0, 1.0, 1, 1, "ACTIVE", 0L);
        IntegrationEvent<RouteEventPayload> event = IntegrationEvent.create(
                RouteEvents.ROUTE_CREATED, Instant.now(), "flightroutes", "flightroutes-2", "x", broken);

        assertThrows(IllegalArgumentException.class, () -> projection.apply(event));
        assertFalse(replica.getBean(ProcessedEventRepository.class).existsById(event.eventId().toString()),
                "a failed event must not be recorded as processed");
    }

    @Test
    void theReplicatedEndpointRequiresATCC() throws Exception {
        HttpResponse<String> anonymous = HTTP.send(HttpRequest.newBuilder(URI.create(url + "/api/routes/replicated"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonymous.statusCode());
    }

    private static JsonNode get(String path, int expectedStatus) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url + path))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
