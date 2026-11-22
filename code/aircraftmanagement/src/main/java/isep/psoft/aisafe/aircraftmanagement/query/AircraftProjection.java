package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.events.AircraftEventPayload;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftEvents;
import isep.sidis.common.messaging.IdempotentEventHandler;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.messaging.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Applies aircraft events to this replica's read models {@code REPLICATED_AIRCRAFT} and {@code AIRCRAFT_MOOD}
 * (same rules as {@code RouteProjection} in Flight Routes):
 * <ul>
 *   <li><b>Idempotent:</b> an {@code eventId} already processed by this replica is ignored, and the record of the
 *       event id commits in the same transaction as the change ({@code JpaProcessedEventStore}).</li>
 *   <li><b>Own events:</b> the owner applies its event inside the write transaction (read-your-writes), so when the
 *       broker delivers the same event back to it, the event id is already recorded and it is skipped.</li>
 *   <li><b>Ordering:</b> each aircraft is written by one replica only (its owner) and each queue has one consumer, so
 *       the events of an aircraft arrive in order. As a safety net, an event whose {@code version} is older than the
 *       stored one is ignored (e.g. a message replayed from the DLQ).</li>
 *   <li>An unknown {@code eventType} is ignored (logged).</li>
 * </ul>
 * Both tables are updated by the SAME event in ONE transaction: one event id, recorded once.
 */
@Service
public class AircraftProjection {

    private static final Logger log = LoggerFactory.getLogger(AircraftProjection.class);

    private final ReplicatedAircraftRepository aircraft;
    private final AircraftMoodRepository moods;
    private final IdempotentEventHandler idempotent;
    private final Clock clock = Clock.systemUTC();

    public AircraftProjection(ReplicatedAircraftRepository aircraft, AircraftMoodRepository moods,
                              ProcessedEventStore processedEvents) {
        this.aircraft = aircraft;
        this.moods = moods;
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    /** @return {@code false} if the event was a duplicate and nothing was done */
    @Transactional
    public boolean apply(IntegrationEvent<AircraftEventPayload> event) {
        return idempotent.handle(event, this::applyOnce);
    }

    private void applyOnce(IntegrationEvent<AircraftEventPayload> event) {
        AircraftEventPayload p = event.payload();
        if (p == null || p.registrationNumber() == null || p.registrationNumber().isBlank()) {
            throw new IllegalArgumentException("Aircraft event " + event.eventId() + " has no registrationNumber");
        }
        switch (event.eventType()) {
            case AircraftEvents.AIRCRAFT_REGISTERED, AircraftEvents.AIRCRAFT_STATUS_CHANGED -> upsert(event, p);
            default -> log.warn("Ignoring unknown aircraft event type {} ({})", event.eventType(), event.eventId());
        }
    }

    private void upsert(IntegrationEvent<AircraftEventPayload> event, AircraftEventPayload p) {
        ReplicatedAircraft row = aircraft.findById(p.registrationNumber())
                .orElseGet(() -> new ReplicatedAircraft(p.registrationNumber()));
        if (row.isNewerThan(p.version())) {
            log.info("Ignoring stale {} for aircraft {} (version {} < stored {})", event.eventType(),
                    p.registrationNumber(), p.version(), row.getSourceVersion());
            return;
        }
        row.apply(p, event.sourceInstance(), event.eventId().toString(), event.timestamp());
        aircraft.save(row);

        AircraftMood mood = moods.findById(p.registrationNumber())
                .orElseGet(() -> new AircraftMood(p.registrationNumber()));
        mood.apply(row.getModelName(), row.getStatus(), clock.instant());
        moods.save(mood);

        log.info("Applied {} for aircraft {} ({}, status {}, owner {}, version {})", event.eventType(),
                p.registrationNumber(), row.getModelName(), row.getStatus(), event.sourceInstance(), p.version());
    }
}
