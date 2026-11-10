package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.events.RouteEventPayload;
import isep.psoft.aisafe.flightroutes.events.RouteEvents;
import isep.sidis.common.messaging.IdempotentEventHandler;
import isep.sidis.common.messaging.IntegrationEvent;
import isep.sidis.common.messaging.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies route events to this replica's read-side table {@code REPLICATED_ROUTE}.
 *
 * <ul>
 *   <li><b>Idempotent:</b> an {@code eventId} already processed by this replica is ignored, and the record of the
 *       event id commits in the same transaction as the change ({@code JpaProcessedEventStore}).</li>
 *   <li><b>Own events:</b> a replica also receives the events it published. They are applied like any other
 *       (an upsert of the same data), so the table holds the routes of ALL replicas, including its own.</li>
 *   <li><b>Ordering:</b> each route is written by one replica only (its owner), and each queue has one consumer,
 *       so events of a route arrive in order. As a safety net, an event whose {@code version} is older than the
 *       stored one is ignored (e.g. a message replayed from the DLQ).</li>
 *   <li>An unknown {@code eventType} is ignored (logged), so Flight Routes can add event types later.</li>
 * </ul>
 */
@Service
public class RouteProjection {

    private static final Logger log = LoggerFactory.getLogger(RouteProjection.class);

    private final ReplicatedRouteRepository repository;
    private final IdempotentEventHandler idempotent;

    public RouteProjection(ReplicatedRouteRepository repository, ProcessedEventStore processedEvents) {
        this.repository = repository;
        this.idempotent = new IdempotentEventHandler(processedEvents);
    }

    /** @return {@code false} if the event was a duplicate and nothing was done */
    @Transactional
    public boolean apply(IntegrationEvent<RouteEventPayload> event) {
        return idempotent.handle(event, this::applyOnce);
    }

    private void applyOnce(IntegrationEvent<RouteEventPayload> event) {
        RouteEventPayload payload = event.payload();
        if (payload == null || payload.routeId() == null) {
            throw new IllegalArgumentException("Route event " + event.eventId() + " has no routeId");
        }
        switch (event.eventType()) {
            case RouteEvents.ROUTE_CREATED, RouteEvents.ROUTE_UPDATED -> upsert(event, payload);
            case RouteEvents.ROUTE_DELETED -> delete(payload);
            default -> log.warn("Ignoring unknown route event type {} ({})", event.eventType(), event.eventId());
        }
    }

    private void upsert(IntegrationEvent<RouteEventPayload> event, RouteEventPayload payload) {
        ReplicatedRoute route = repository.findById(payload.routeId()).orElseGet(() -> new ReplicatedRoute(payload.routeId()));
        if (route.isNewerThan(payload.version())) {
            log.info("Ignoring stale {} for route {} (version {} < stored {})", event.eventType(), payload.routeId(),
                    payload.version(), route.getSourceVersion());
            return;
        }
        route.apply(payload, event.sourceInstance(), event.eventId().toString(), event.timestamp());
        repository.save(route);
        log.info("Applied {} for route {} {}->{} (owner {}, version {})", event.eventType(), payload.routeId(),
                payload.originIata(), payload.destinationIata(), event.sourceInstance(), payload.version());
    }

    private void delete(RouteEventPayload payload) {
        repository.findById(payload.routeId()).ifPresent(route -> {
            repository.delete(route);
            log.info("Applied RouteDeleted for route {}", payload.routeId());
        });
    }
}
