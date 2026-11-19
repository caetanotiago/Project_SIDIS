package isep.psoft.aisafe.messaging;

import isep.psoft.aisafe.FlightRoutesApplication;
import isep.psoft.aisafe.flightroutes.command.IntegrationEventTransport;
import isep.psoft.aisafe.flightroutes.events.RouteEventPayload;
import isep.psoft.aisafe.flightroutes.events.RouteEvents;
import isep.psoft.aisafe.flightroutes.events.ScheduledFlightEventPayload;
import isep.psoft.aisafe.flightroutes.events.ScheduledFlightEvents;
import isep.psoft.aisafe.flightroutes.query.FlightRoutesEventRouter;
import isep.psoft.aisafe.flightroutes.query.FlightRoutesEventsListener;
import isep.psoft.aisafe.flightroutes.query.ReplicatedRouteRepository;
import isep.psoft.aisafe.flightroutes.query.RouteProjection;
import isep.psoft.aisafe.flightroutes.query.ScheduledFlightProjection;
import isep.psoft.aisafe.infrastructure.messaging.ProcessedEventRepository;
import isep.sidis.common.http.AisafeHeaders;
import isep.sidis.common.messaging.EventMessageConverters;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The read models built from events (routes and scheduled flights) in one real replica with its own H2 database,
 * WITHOUT a broker: events are handed to the projections / router directly, exactly as {@link FlightRoutesEventsListener}
 * does. Every check goes through the EXISTING REST endpoints, which now read the read models.
 * Also checks that with messaging off (the default in tests) no AMQP bean is active.
 */
class ReadModelProjectionsTest {

    private static final String SELF = "it-projection";
    private static final String SERVICE_KEY = "it-service-key";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static ConfigurableApplicationContext replica;
    private static RouteProjection routes;
    private static ScheduledFlightProjection flights;
    private static FlightRoutesEventRouter router;
    private static String url;
    private static String token;
    private static String backoffice;

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
                "--aisafe.security.service-key=" + SERVICE_KEY,
                "--aisafe.bootstrap.enabled=false",
                "--logging.file.name=target/it-logs/projection.log");
        routes = replica.getBean(RouteProjection.class);
        flights = replica.getBean(ScheduledFlightProjection.class);
        router = replica.getBean(FlightRoutesEventRouter.class);
        JwtTokenProvider tokens = replica.getBean(JwtTokenProvider.class);
        token = tokens.generateToken("atcc-it", List.of("ROLE_ATCC"));
        backoffice = tokens.generateToken("bo-it", List.of("ROLE_BACKOFFICE_OPERATOR"));
    }

    @AfterAll
    static void stop() {
        if (replica != null) replica.close();
    }

    private static <T> IntegrationEvent<T> event(String type, String source, String aggregateId, T payload) {
        return IntegrationEvent.create(type, Instant.now(), "flightroutes", source, aggregateId, payload);
    }

    private static IntegrationEvent<RouteEventPayload> routeEvent(String type, String source, RouteEventPayload p) {
        return event(type, source, p.routeId(), p);
    }

    private static RouteEventPayload route(String id, String origin, String dest, int minCapacity, String status,
                                           long version) {
        var created = new RouteEventPayload.HistoryEntry(LocalDateTime.parse("2026-11-18T10:00:00"), "Route created.",
                null, null, null, null, 400.0, 120, 55, "ACTIVE");
        return new RouteEventPayload(id, origin, dest, 274.0, 400.0, minCapacity, 55, status, version,
                List.of(created));
    }

    private static ScheduledFlightEventPayload flight(String id, String routeId, String reg, String date, String status,
                                                      long version) {
        return new ScheduledFlightEventPayload(id, reg, routeId, "OPO", "LIS", 55, LocalDate.parse(date),
                LocalTime.parse("08:00"), status, version);
    }

    // ------------------------------------------------------------------ messaging off

    @Test
    void messagingIsOffByDefaultInTestsSoNoAmqpBeanIsActive() {
        assertTrue(replica.getBeansOfType(EventPublisher.class).isEmpty());
        assertTrue(replica.getBeansOfType(FlightRoutesEventsListener.class).isEmpty());
        assertTrue(replica.getBeansOfType(IntegrationEventTransport.class).isEmpty());
    }

    // ------------------------------------------------------------------ routes

    @Test
    void createdThenUpdatedRouteEventsAreServedByTheExistingRouteEndpoints() throws Exception {
        String id = UUID.randomUUID().toString();

        assertTrue(routes.apply(routeEvent(RouteEvents.ROUTE_CREATED, "flightroutes-2", route(id, "FAO", "FNC", 120, "ACTIVE", 0))));
        assertTrue(routes.apply(routeEvent(RouteEvents.ROUTE_UPDATED, "flightroutes-2", route(id, "FAO", "FNC", 150, "INACTIVE", 1))));

        JsonNode body = get("/api/routes/" + id, 200);
        assertEquals("FAO", body.get("originIATA").asString());
        assertEquals(150, body.get("minCapacity").asInt());
        assertEquals("INACTIVE", body.get("status").asString());
        assertEquals("flightroutes-2", replica.getBean(ReplicatedRouteRepository.class).findById(id).orElseThrow()
                .getOwnerInstance());

        assertEquals(List.of(id), ids(get("/api/routes/search?origin=FAO&dest=FNC", 200)));
        assertEquals("Route created.", get("/api/routes/" + id + "/history", 200).get(0).get("description").asString());
        // INACTIVE -> not part of the network nor compatible
        assertFalse(ids(get("/api/routes/active?sortBy=distance", 200)).contains(id));
    }

    @Test
    void aRedeliveredEventIsAppliedOnlyOnceAndItsIdIsStoredInTheReplicaDatabase() {
        String id = UUID.randomUUID().toString();
        var created = routeEvent(RouteEvents.ROUTE_CREATED, "flightroutes-3", route(id, "OPO", "PDL", 120, "ACTIVE", 0));

        assertTrue(routes.apply(created));
        assertFalse(routes.apply(created), "same eventId: must be ignored");

        assertTrue(replica.getBean(ProcessedEventRepository.class).existsById(created.eventId().toString()));
        assertEquals(created.eventId().toString(), replica.getBean(ReplicatedRouteRepository.class).findById(id)
                .orElseThrow().getLastEventId());
    }

    @Test
    void anEventOlderThanTheStoredVersionIsIgnored() throws Exception {
        String id = UUID.randomUUID().toString();
        routes.apply(routeEvent(RouteEvents.ROUTE_CREATED, "flightroutes-2", route(id, "OPO", "TER", 120, "ACTIVE", 0)));
        routes.apply(routeEvent(RouteEvents.ROUTE_UPDATED, "flightroutes-2", route(id, "OPO", "TER", 200, "ACTIVE", 2)));

        routes.apply(routeEvent(RouteEvents.ROUTE_UPDATED, "flightroutes-2", route(id, "OPO", "TER", 130, "ACTIVE", 1)));

        assertEquals(200, get("/api/routes/" + id, 200).get("minCapacity").asInt());
    }

    @Test
    void theRouterConvertsAJsonPayloadAsItArrivesFromTheBrokerAndIgnoresUnknownTypes() throws Exception {
        String id = UUID.randomUUID().toString();
        JsonMapper wire = EventMessageConverters.jsonMapper();
        JsonNode payload = wire.valueToTree(route(id, "LIS", "HOR", 120, "ACTIVE", 0));

        assertTrue(router.apply(event(RouteEvents.ROUTE_CREATED, "flightroutes-2", id, payload)));
        assertEquals("HOR", get("/api/routes/" + id, 200).get("destIATA").asString());

        assertFalse(router.apply(event("RouteRenamed", "flightroutes-2", id, payload)));
    }

    @Test
    void anEventWithoutRouteIdFailsSoTheContainerRetriesAndDeadLettersIt() {
        RouteEventPayload broken = new RouteEventPayload(null, "OPO", "LIS", 1.0, 1.0, 1, 1, "ACTIVE", 0L, null);
        var event = event(RouteEvents.ROUTE_CREATED, "flightroutes-2", "x", broken);

        assertThrows(IllegalArgumentException.class, () -> routes.apply(event));
        assertFalse(replica.getBean(ProcessedEventRepository.class).existsById(event.eventId().toString()),
                "a failed event must not be recorded as processed");
    }

    @Test
    void countByAirportAndCompatibleRoutesComeFromTheReadModel() throws Exception {
        String id = UUID.randomUUID().toString();
        routes.apply(routeEvent(RouteEvents.ROUTE_CREATED, "flightroutes-3", route(id, "BCN", "BIO", 90, "ACTIVE", 0)));

        JsonNode counts = getWithKey("/api/routes/statistics/count-by-airport", backoffice);
        assertEquals(1, counts.get("BCN").asLong());
        assertEquals(1, counts.get("BIO").asLong());

        JsonNode compatible = getWithKey("/api/routes/compatible?range=500&capacity=100", token);
        assertTrue(ids(compatible).contains(id));
        JsonNode tooSmall = getWithKey("/api/routes/compatible?range=500&capacity=80", token);
        assertFalse(ids(tooSmall).contains(id));
    }

    // ------------------------------------------------------------------ scheduled flights

    @Test
    void scheduledFlightEventsFeedTheFlightQueriesAndStatistics() throws Exception {
        String routeId = UUID.randomUUID().toString();
        String flightId = UUID.randomUUID().toString();
        routes.apply(routeEvent(RouteEvents.ROUTE_CREATED, "flightroutes-2", route(routeId, "OPO", "SVQ", 120, "ACTIVE", 0)));

        assertTrue(flights.apply(event(ScheduledFlightEvents.SCHEDULED_FLIGHT_CREATED, "flightroutes-3", flightId,
                flight(flightId, routeId, "CS-PRJ", "2026-12-01", "SCHEDULED", 0))));

        JsonNode byId = get("/api/scheduled-flights/" + flightId, 200);
        assertEquals("CS-PRJ", byId.get("aircraftRegistration").asString());
        assertEquals(routeId, byId.get("routeID").asString());
        assertTrue(byId.get("_links").get("self").get("href").asString().startsWith(url));

        assertEquals(1, get("/api/scheduled-flights/search?aircraft=CS-PRJ&date=2026-12-01", 200).size());
        assertEquals(0, get("/api/scheduled-flights/search?aircraft=CS-PRJ&date=2026-12-02", 200).size());

        assertEquals(1, getWithKey("/api/scheduled-flights/statistics/usage-by-route", token).get(routeId).asLong());
        assertEquals(55, getWithKey("/api/scheduled-flights/statistics/minutes-by-aircraft", backoffice)
                .get("CS-PRJ").asLong());

        // Reserved event type: the read model already applies status changes
        assertTrue(flights.apply(event(ScheduledFlightEvents.SCHEDULED_FLIGHT_STATUS_CHANGED, "flightroutes-3", flightId,
                flight(flightId, routeId, "CS-PRJ", "2026-12-01", "DELAYED", 1))));
        assertEquals("DELAYED", get("/api/scheduled-flights/" + flightId, 200).get("status").asString());
        // ...and ignores an older one
        flights.apply(event(ScheduledFlightEvents.SCHEDULED_FLIGHT_STATUS_CHANGED, "flightroutes-3", flightId,
                flight(flightId, routeId, "CS-PRJ", "2026-12-01", "SCHEDULED", 0)));
        assertEquals("DELAYED", get("/api/scheduled-flights/" + flightId, 200).get("status").asString());
    }

    @Test
    void anUnknownFlightIs404AndAFlightEventWithoutIdIsRejected() throws Exception {
        get("/api/scheduled-flights/" + UUID.randomUUID(), 404);
        var broken = event(ScheduledFlightEvents.SCHEDULED_FLIGHT_CREATED, "flightroutes-2", "x",
                flight(null, "r", "CS-X", "2026-12-01", "SCHEDULED", 0));
        assertThrows(IllegalArgumentException.class, () -> flights.apply(broken));
    }

    // ------------------------------------------------------------------ helpers

    private static JsonNode get(String path, int expectedStatus) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url + path))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    private static JsonNode getWithKey(String path, String jwt) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url + path))
                .header("Authorization", "Bearer " + jwt).header(AisafeHeaders.SERVICE_KEY, SERVICE_KEY)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), path + " -> " + response.body());
        return JSON.readTree(response.body());
    }

    private static List<String> ids(JsonNode array) {
        return array.valueStream().map(n -> n.get("id").asString()).toList();
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
