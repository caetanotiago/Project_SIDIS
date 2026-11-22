package isep.psoft.aisafe.messaging;

import isep.psoft.aisafe.AircraftManagementApplication;
import isep.psoft.aisafe.aircraftmanagement.command.IntegrationEventTransport;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftEventPayload;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftEvents;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftModelEventPayload;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftModelEvents;
import isep.psoft.aisafe.aircraftmanagement.query.AircraftManagementEventRouter;
import isep.psoft.aisafe.aircraftmanagement.query.AircraftManagementEventsListener;
import isep.psoft.aisafe.aircraftmanagement.query.AircraftMoodRepository;
import isep.psoft.aisafe.aircraftmanagement.query.ReplicatedAircraftRepository;
import isep.psoft.aisafe.aircraftmanagement.query.SearchAircraftModelsService;
import isep.psoft.aisafe.aircraftmanagement.query.external.FlightRoutesEventsListener;
import isep.psoft.aisafe.infrastructure.messaging.ProcessedEventRepository;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.EventPublisher;
import isep.sidis.common.messaging.IntegrationEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The read models built from Aircraft Management's own events in one real replica with its own H2 database, WITHOUT a
 * broker: events are handed to {@link AircraftManagementEventRouter} directly (as JSON trees, exactly as
 * {@link AircraftManagementEventsListener} receives them). Checks idempotency, stale versions, unknown types, partial
 * (status-only) events and that with messaging off (the default in tests) no AMQP bean is active.
 */
class ReadModelProjectionsTest {

    private static final JsonMapper WIRE = EventMessageConverters.jsonMapper();

    private static ConfigurableApplicationContext replica;
    private static AircraftManagementEventRouter router;
    private static ReplicatedAircraftRepository aircraft;
    private static AircraftMoodRepository moods;

    @BeforeAll
    static void start() throws IOException {
        replica = new SpringApplicationBuilder(AircraftManagementApplication.class).run(
                "--server.port=" + freePort(),
                "--aisafe.instance-id=it-projection",
                "--spring.datasource.url=jdbc:h2:mem:it-am-projection;DB_CLOSE_DELAY=-1",
                "--aisafe.peers.urls=",
                "--aisafe.bootstrap.enabled=false",
                "--logging.file.name=target/it-logs/projection.log");
        router = replica.getBean(AircraftManagementEventRouter.class);
        aircraft = replica.getBean(ReplicatedAircraftRepository.class);
        moods = replica.getBean(AircraftMoodRepository.class);
    }

    @AfterAll
    static void stop() {
        if (replica != null) replica.close();
    }

    /** The event as it arrives from RabbitMQ: payload as a JSON tree. */
    private static IntegrationEvent<JsonNode> wire(String type, String aggregateId, Object payload) {
        IntegrationEvent<Object> e = IntegrationEvent.create(type, Instant.now(), "aircraftmanagement",
                "aircraftmanagement-9", aggregateId, payload);
        JsonNode tree = WIRE.valueToTree(payload);
        return new IntegrationEvent<>(e.eventId(), e.eventType(), e.timestamp(), e.sourceService(),
                e.sourceInstance(), e.aggregateId(), tree);
    }

    @Test
    void messagingIsOffByDefaultInTestsSoNoAmqpBeanIsActive() {
        assertTrue(replica.getBeansOfType(EventPublisher.class).isEmpty());
        assertTrue(replica.getBeansOfType(AircraftManagementEventsListener.class).isEmpty());
        assertTrue(replica.getBeansOfType(FlightRoutesEventsListener.class).isEmpty());
        assertTrue(replica.getBeansOfType(IntegrationEventTransport.class).isEmpty());
    }

    @Test
    void aircraftEventsAreAppliedOnceAndStaleOnesAreIgnored() {
        IntegrationEvent<JsonNode> registered = wire(AircraftEvents.AIRCRAFT_REGISTERED, "CS-PRJ",
                new AircraftEventPayload("CS-PRJ", "A320", 180, LocalDate.of(2019, 5, 14), "AVAILABLE", 0L));
        assertTrue(router.apply(registered));
        assertFalse(router.apply(registered), "same eventId twice -> duplicate, ignored");
        assertTrue(replica.getBean(ProcessedEventRepository.class).existsById(registered.eventId().toString()));
        assertEquals("HAPPY", moods.findById("CS-PRJ").orElseThrow().getMood());

        // Status-only event: null fields keep their value
        assertTrue(router.apply(wire(AircraftEvents.AIRCRAFT_STATUS_CHANGED, "CS-PRJ",
                new AircraftEventPayload("CS-PRJ", null, null, null, "UNDER_MAINTENANCE", 2L))));
        // An older event (e.g. replayed from the DLQ) must not undo it
        router.apply(wire(AircraftEvents.AIRCRAFT_STATUS_CHANGED, "CS-PRJ",
                new AircraftEventPayload("CS-PRJ", "A320", 180, null, "AVAILABLE", 1L)));

        var row = aircraft.findById("CS-PRJ").orElseThrow();
        assertEquals("UNDER_MAINTENANCE", row.getStatus());
        assertEquals("A320", row.getModelName());
        assertEquals(180, row.getSeatingCapacity());
        assertEquals(2L, row.getSourceVersion());
        assertEquals("aircraftmanagement-9", row.getOwnerInstance());
        assertEquals("GROUNDED_AND_GRUMPY", moods.findById("CS-PRJ").orElseThrow().getMood());
    }

    @Test
    void modelEventsKeepTheRangeCurrent() {
        router.apply(wire(AircraftModelEvents.AIRCRAFT_MODEL_CREATED, "E190",
                new AircraftModelEventPayload("E190", "Embraer", 100, 13000.0, 4500.0, 823.0, 0L)));
        router.apply(wire(AircraftModelEvents.AIRCRAFT_MODEL_UPDATED, "E190",
                new AircraftModelEventPayload("E190", "Embraer", 100, 13000.0, 4700.0, 823.0, 1L)));

        assertEquals(4700.0, replica.getBean(SearchAircraftModelsService.class)
                .getByName("E190").maximumRange());
    }

    @Test
    void unknownEventTypesAreIgnored() {
        assertFalse(router.apply(wire("AircraftPainted", "CS-PRJ", new AircraftEventPayload("CS-PRJ", null, null,
                null, "AVAILABLE", 9L))));
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
