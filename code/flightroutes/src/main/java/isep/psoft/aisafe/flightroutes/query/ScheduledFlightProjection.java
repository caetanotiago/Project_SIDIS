package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.events.ScheduledFlightEventPayload;
import isep.psoft.aisafe.flightroutes.events.ScheduledFlightEvents;
import isep.sidis.common.messaging.IdempotentEventHandler;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.messaging.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies scheduled-flight events to this replica's read model {@code REPLICATED_SCHEDULED_FLIGHT}, with the same
 * rules as {@link RouteProjection}: idempotent by eventId, stale versions ignored, unknown types ignored.
 */
@Service
public class ScheduledFlightProjection {

    private static final Logger log = LoggerFactory.getLogger(ScheduledFlightProjection.class);

    private final ReplicatedScheduledFlightRepository repository;
    private final IdempotentEventHandler idempotent;

    public ScheduledFlightProjection(ReplicatedScheduledFlightRepository repository, ProcessedEventStore processedEvents) {
        this.repository = repository;
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    /** @return {@code false} if the event was a duplicate and nothing was done */
    @Transactional
    public boolean apply(IntegrationEvent<ScheduledFlightEventPayload> event) {
        return idempotent.handle(event, this::applyOnce);
    }

    private void applyOnce(IntegrationEvent<ScheduledFlightEventPayload> event) {
        ScheduledFlightEventPayload p = event.payload();
        if (p == null || p.flightId() == null) {
            throw new IllegalArgumentException("Scheduled-flight event " + event.eventId() + " has no flightId");
        }
        switch (event.eventType()) {
            case ScheduledFlightEvents.SCHEDULED_FLIGHT_CREATED -> upsert(event, p);
            case ScheduledFlightEvents.SCHEDULED_FLIGHT_STATUS_CHANGED -> changeStatus(event, p);
            default -> log.warn("Ignoring unknown scheduled-flight event type {} ({})", event.eventType(), event.eventId());
        }
    }

    private void upsert(IntegrationEvent<ScheduledFlightEventPayload> event, ScheduledFlightEventPayload p) {
        ReplicatedScheduledFlight flight = repository.findById(p.flightId())
                .orElseGet(() -> new ReplicatedScheduledFlight(p.flightId()));
        if (flight.isNewerThan(p.version())) {
            log.info("Ignoring stale {} for flight {} (version {} < stored {})", event.eventType(), p.flightId(),
                    p.version(), flight.getSourceVersion());
            return;
        }
        flight.apply(p, event.sourceInstance(), event.eventId().toString(), event.timestamp());
        repository.save(flight);
        log.info("Applied {} for flight {} ({} on route {} at {} {}, owner {})", event.eventType(), p.flightId(),
                p.aircraftRegistration(), p.routeId(), p.date(), p.time(), event.sourceInstance());
    }

    private void changeStatus(IntegrationEvent<ScheduledFlightEventPayload> event, ScheduledFlightEventPayload p) {
        ReplicatedScheduledFlight flight = repository.findById(p.flightId()).orElse(null);
        if (flight == null) {
            // State-carrying payload: a status change seen before the creation still has every field.
            upsert(event, p);
            return;
        }
        if (flight.isNewerThan(p.version())) {
            log.info("Ignoring stale {} for flight {}", event.eventType(), p.flightId());
            return;
        }
        flight.applyStatus(p.status(), p.version(), event.eventId().toString(), event.timestamp());
        repository.save(flight);
        log.info("Applied {} for flight {}: {}", event.eventType(), p.flightId(), p.status());
    }
}
