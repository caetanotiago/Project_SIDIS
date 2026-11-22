package isep.psoft.aisafe.aircraftmanagement.query.external;

import isep.psoft.aisafe.aircraftmanagement.events.external.FlightRoutesEvents;
import isep.psoft.aisafe.aircraftmanagement.events.external.RoutePayload;
import isep.psoft.aisafe.aircraftmanagement.events.external.ScheduledFlightPayload;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.IdempotentEventHandler;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.messaging.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applies Flight Routes events to the local read models {@code KNOWN_ROUTE} and {@code KNOWN_SCHEDULED_FLIGHT}. Same
 * rules as the projections of Aircraft Management's own events: idempotent by eventId (recorded in the same
 * transaction), stale versions ignored, unknown types ignored (so Flight Routes can add events freely).
 * Fed by {@link FlightRoutesEventsListener}.
 */
@Service
public class FlightRoutesEventsProjection {

    private static final Logger log = LoggerFactory.getLogger(FlightRoutesEventsProjection.class);
    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();

    private final KnownRouteRepository routes;
    private final KnownScheduledFlightRepository flights;
    private final IdempotentEventHandler idempotent;

    public FlightRoutesEventsProjection(KnownRouteRepository routes, KnownScheduledFlightRepository flights,
                                        ProcessedEventStore processedEvents) {
        this.routes = routes;
        this.flights = flights;
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    /** @return {@code false} if the event was a duplicate */
    @Transactional
    public boolean apply(IntegrationEvent<?> event) {
        return idempotent.handle(event, this::applyOnce);
    }

    private void applyOnce(IntegrationEvent<?> event) {
        switch (event.eventType()) {
            case FlightRoutesEvents.ROUTE_CREATED, FlightRoutesEvents.ROUTE_UPDATED -> {
                RoutePayload p = payload(event, RoutePayload.class);
                require(p.routeId(), "routeId", event);
                KnownRoute route = routes.findById(p.routeId()).orElseGet(() -> new KnownRoute(p.routeId()));
                if (route.isNewerThan(p.version())) {
                    log.info("Ignoring stale {} for route {}", event.eventType(), p.routeId());
                    return;
                }
                route.apply(p, event.timestamp());
                routes.save(route);
            }
            case FlightRoutesEvents.SCHEDULED_FLIGHT_CREATED, FlightRoutesEvents.SCHEDULED_FLIGHT_STATUS_CHANGED -> {
                ScheduledFlightPayload p = payload(event, ScheduledFlightPayload.class);
                require(p.flightId(), "flightId", event);
                KnownScheduledFlight flight = flights.findById(p.flightId())
                        .orElseGet(() -> new KnownScheduledFlight(p.flightId()));
                if (flight.isNewerThan(p.version())) {
                    log.info("Ignoring stale {} for flight {}", event.eventType(), p.flightId());
                    return;
                }
                flight.apply(p, event.timestamp());
                flights.save(flight);
            }
            default -> {
                log.debug("Ignoring {} from {} (not used by Aircraft Management)", event.eventType(),
                        event.sourceService());
                return;
            }
        }
        log.info("Applied {} {} from {}", event.eventType(), event.aggregateId(), event.sourceInstance());
    }

    private static <T> T payload(IntegrationEvent<?> event, Class<T> type) {
        Object raw = event.payload();
        if (raw == null) {
            throw new IllegalArgumentException(event.eventType() + " " + event.eventId() + " has no payload");
        }
        return type.isInstance(raw) ? type.cast(raw) : JSON.convertValue(raw, type);
    }

    private static void require(String value, String field, IntegrationEvent<?> event) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(event.eventType() + " " + event.eventId() + " has no " + field);
        }
    }
}
