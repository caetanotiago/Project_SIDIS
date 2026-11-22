package isep.psoft.aisafe.messaging;

import isep.psoft.aisafe.FlightRoutesApplication;
import isep.psoft.aisafe.flightroutes.clients.AircraftClient;
import isep.psoft.aisafe.flightroutes.clients.AircraftSnapshot;
import isep.psoft.aisafe.flightroutes.clients.AirportClient;
import isep.psoft.aisafe.flightroutes.events.external.AircraftEventPayload;
import isep.psoft.aisafe.flightroutes.events.external.AircraftModelEventPayload;
import isep.psoft.aisafe.flightroutes.events.external.AirportEventPayload;
import isep.psoft.aisafe.flightroutes.events.external.ExternalEvents;
import isep.psoft.aisafe.flightroutes.query.external.ExternalEventsListener;
import isep.psoft.aisafe.flightroutes.query.external.ExternalEventsProjection;
import isep.psoft.aisafe.flightroutes.query.external.ExternalReadModels;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.remote.RemoteServiceUnavailableException;
import isep.sidis.common.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consumer-side scaffolding for the events of Aircraft Management and Airports (B1/B2 migration), WITHOUT a broker.
 * Replica "on" has {@code aisafe.flightroutes.external-events.enabled=true}; both replicas point the aircraft and
 * airports URLs to a port where nothing listens, so ANY HTTP call to those services fails. Events are handed to
 * {@link ExternalEventsProjection} directly, with a JSON payload as they would arrive from RabbitMQ.
 */
class ExternalEventsScaffoldingTest {

    private static final JsonMapper WIRE = EventMessageConverters.jsonMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static ConfigurableApplicationContext on;
    private static ConfigurableApplicationContext off;
    private static String urlOn;

    @BeforeAll
    static void start() throws IOException {
        String nobody = "http://localhost:" + freePort();
        int port = freePort();
        urlOn = "http://localhost:" + port;
        on = startReplica("ext-on", port, nobody, true);
        off = startReplica("ext-off", freePort(), nobody, false);
    }

    @AfterAll
    static void stop() {
        if (on != null) on.close();
        if (off != null) off.close();
    }

    @Test
    void withoutABrokerNoListenerIsActiveAndTheFlagIsReadPerReplica() {
        assertTrue(on.getBeansOfType(ExternalEventsListener.class).isEmpty(), "listeners need messaging on");
        assertTrue(on.getBean(ExternalReadModels.class).isEnabled());
        assertFalse(off.getBean(ExternalReadModels.class).isEnabled());
    }

    @Test
    void aircraftEventsReplaceTheB1HttpCallsWhenEnabled() {
        ExternalEventsProjection projection = on.getBean(ExternalEventsProjection.class);
        assertTrue(projection.apply(external(ExternalEvents.AIRCRAFT_SERVICE, ExternalEvents.AIRCRAFT_MODEL_CREATED, "A320",
                new AircraftModelEventPayload("A320", 6100.0, 0L))));
        assertTrue(projection.apply(external(ExternalEvents.AIRCRAFT_SERVICE, ExternalEvents.AIRCRAFT_REGISTERED, "CS-EXT",
                new AircraftEventPayload("CS-EXT", "A320", 180, "AVAILABLE", 0L))));

        AircraftSnapshot aircraft = on.getBean(AircraftClient.class).getAircraft("CS-EXT");
        assertEquals(6100.0, aircraft.maximumRange());
        assertTrue(aircraft.isAvailable());

        // Status-only event (null fields keep their value), then an older event that must be ignored
        projection.apply(external(ExternalEvents.AIRCRAFT_SERVICE, ExternalEvents.AIRCRAFT_STATUS_CHANGED, "CS-EXT",
                new AircraftEventPayload("CS-EXT", null, null, "UNDER_MAINTENANCE", 2L)));
        projection.apply(external(ExternalEvents.AIRCRAFT_SERVICE, ExternalEvents.AIRCRAFT_STATUS_CHANGED, "CS-EXT",
                new AircraftEventPayload("CS-EXT", null, null, "AVAILABLE", 1L)));
        AircraftSnapshot later = on.getBean(AircraftClient.class).getAircraft("CS-EXT");
        assertEquals("UNDER_MAINTENANCE", later.status());
        assertEquals(180, later.seatingCapacity());

        // US201 in Aircraft Management: AircraftModelUpdated keeps the local copy of the model's range current
        assertTrue(projection.apply(external(ExternalEvents.AIRCRAFT_SERVICE, ExternalEvents.AIRCRAFT_MODEL_UPDATED, "A320",
                new AircraftModelEventPayload("A320", 6300.0, 1L))));
        assertEquals(6300.0, on.getBean(AircraftClient.class).getAircraft("CS-EXT").maximumRange());

        // Unknown locally -> falls back to HTTP (here: nobody listens -> 503 as in Assignment 1)
        assertThrows(RemoteServiceUnavailableException.class, () -> on.getBean(AircraftClient.class).getAircraft("CS-NONE"));
    }

    @Test
    void aircraftModelUpdatedKeepsTheRangeCurrentWhateverTheOrderOfArrival() {
        ExternalEventsProjection projection = on.getBean(ExternalEventsProjection.class);
        AircraftClient client = on.getBean(AircraftClient.class);

        // Update of a model this replica never saw (created before its queue existed): stored anyway (upsert).
        // Exact JSON published by Aircraft Management: the extra fields are ignored.
        assertTrue(projection.apply(external(ExternalEvents.AIRCRAFT_SERVICE, ExternalEvents.AIRCRAFT_MODEL_UPDATED, "E190",
                json("{\"modelName\":\"E190\",\"manufacturer\":\"Embraer\",\"seatingCapacity\":100,"
                        + "\"fuelCapacity\":12971.0,\"maximumRange\":4000.0,\"cruisingSpeed\":829.0,\"version\":1}"))));
        assertTrue(projection.apply(external(ExternalEvents.AIRCRAFT_SERVICE, ExternalEvents.AIRCRAFT_REGISTERED, "CS-UPD",
                json("{\"registrationNumber\":\"CS-UPD\",\"modelName\":\"E190\",\"seatingCapacity\":100,"
                        + "\"manufacturingDate\":\"2020-01-15\",\"status\":\"AVAILABLE\",\"version\":0}"))));
        assertEquals(4000.0, client.getAircraft("CS-UPD").maximumRange());

        // The creation (version 0) arrives late: older than the stored version -> ignored
        projection.apply(external(ExternalEvents.AIRCRAFT_SERVICE, ExternalEvents.AIRCRAFT_MODEL_CREATED, "E190",
                new AircraftModelEventPayload("E190", 3500.0, 0L)));
        assertEquals(4000.0, client.getAircraft("CS-UPD").maximumRange());

        // A newer update is applied; the same event delivered again by the broker is a duplicate
        IntegrationEvent<?> updatedAgain = external(ExternalEvents.AIRCRAFT_SERVICE,
                ExternalEvents.AIRCRAFT_MODEL_UPDATED, "E190", new AircraftModelEventPayload("E190", 4500.0, 2L));
        assertTrue(projection.apply(updatedAgain));
        assertFalse(projection.apply(updatedAgain));
        assertEquals(4500.0, client.getAircraft("CS-UPD").maximumRange());

        // Republished by Aircraft Management at start-up (AircraftModelCreated with the CURRENT version): no change
        assertTrue(projection.apply(external(ExternalEvents.AIRCRAFT_SERVICE, ExternalEvents.AIRCRAFT_MODEL_CREATED, "E190",
                new AircraftModelEventPayload("E190", 4500.0, 2L))));
        assertEquals(4500.0, client.getAircraft("CS-UPD").maximumRange());
    }

    @Test
    void airportEventsReplaceTheB2HttpCallsSoARouteCanBeCreatedWithAirportsDown() throws Exception {
        ExternalEventsProjection projection = on.getBean(ExternalEventsProjection.class);
        projection.apply(external(ExternalEvents.AIRPORTS_SERVICE, ExternalEvents.AIRPORT_CREATED, "OPO",
                new AirportEventPayload("OPO", 41.2481, -8.6814, "OPERATIONAL", 0L)));
        projection.apply(external(ExternalEvents.AIRPORTS_SERVICE, ExternalEvents.AIRPORT_CREATED, "LIS",
                new AirportEventPayload("LIS", 38.7742, -9.1342, "OPERATIONAL", 0L)));

        assertTrue(on.getBean(AirportClient.class).exists("opo"));

        String token = on.getBean(JwtTokenProvider.class).generateToken("atcc-it", List.of("ROLE_ATCC"));
        HttpResponse<String> created = HTTP.send(HttpRequest.newBuilder(URI.create(urlOn + "/api/routes"))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"originIATA\":\"OPO\",\"destIATA\":\"LIS\",\"estimatedFlightTime\":55,\"minRange\":400,\"minCapacity\":120}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, created.statusCode(), created.body());

        projection.apply(external(ExternalEvents.AIRPORTS_SERVICE, ExternalEvents.AIRPORT_STATUS_CHANGED, "LIS",
                new AirportEventPayload("LIS", null, null, "CLOSED", 1L)));
        assertFalse(on.getBean(AirportClient.class).getAirport("LIS").isOperational());
    }

    @Test
    void withTheFlagOffTheLocalCopiesAreNeverUsed() {
        ExternalEventsProjection projection = off.getBean(ExternalEventsProjection.class);
        projection.apply(external(ExternalEvents.AIRPORTS_SERVICE, ExternalEvents.AIRPORT_CREATED, "FAO",
                new AirportEventPayload("FAO", 37.0, -7.9, "OPERATIONAL", 0L)));

        assertThrows(RemoteServiceUnavailableException.class, () -> off.getBean(AirportClient.class).getAirport("FAO"));
    }

    @Test
    void eventsOfOtherTypesAreIgnoredAndInvalidOnesAreRejected() {
        ExternalEventsProjection projection = on.getBean(ExternalEventsProjection.class);
        assertTrue(projection.apply(external(ExternalEvents.AIRCRAFT_SERVICE, "AircraftDecommissioned", "CS-EXT",
                new AircraftEventPayload("CS-EXT", null, null, null, 9L))));
        assertThrows(IllegalArgumentException.class, () -> projection.apply(external(ExternalEvents.AIRPORTS_SERVICE,
                ExternalEvents.AIRPORT_CREATED, "?", new AirportEventPayload(null, 1.0, 1.0, "OPERATIONAL", 0L))));
    }

    /** An event of another service, with the payload as a JSON tree (as the listener receives it). */
    private static IntegrationEvent<?> external(String service, String type, String aggregateId, Object payload) {
        return IntegrationEvent.create(type, Instant.now(), service, service + "-1", aggregateId,
                WIRE.valueToTree(payload));
    }

    /** A payload exactly as another service serialises it (not built from Flight Routes' own records). */
    private static Object json(String raw) {
        return WIRE.readTree(raw);
    }

    private static ConfigurableApplicationContext startReplica(String id, int port, String nobody, boolean external) {
        return new SpringApplicationBuilder(FlightRoutesApplication.class).run(
                "--server.port=" + port,
                "--aisafe.instance-id=" + id,
                "--spring.datasource.url=jdbc:h2:mem:" + id + ";DB_CLOSE_DELAY=-1",
                "--aisafe.peers.urls=",
                "--services.aircraft.urls=" + nobody,
                "--services.airports.urls=" + nobody,
                "--aisafe.http.max-retries=0",
                "--aisafe.http.connect-timeout=300ms",
                "--aisafe.bootstrap.enabled=false",
                "--aisafe.flightroutes.external-events.enabled=" + external,
                "--logging.file.name=target/it-logs/" + id + ".log");
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
