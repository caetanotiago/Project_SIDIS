package isep.psoft.aisafe.flightroutes.command;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.events.RouteEventPayload;
import isep.psoft.aisafe.flightroutes.events.RouteEvents;
import isep.sidis.common.messaging.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes {@code RouteCreated} / {@code RouteUpdated} / {@code RouteDeleted} to {@code aisafe.flightroutes.events}
 * AFTER the local transaction has committed (only when {@code aisafe.messaging.enabled=true}).
 *
 * <p>Known gap (no transactional outbox yet): if the broker is down at that moment the change is committed but the
 * event is lost; it is logged as an error and the HTTP request still succeeds. The other replicas' read models then
 * miss that change until a later event of the same route.
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class RouteIntegrationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RouteIntegrationEventPublisher.class);

    private final EventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RouteChanged change) {
        FlightRoute route = change.route();
        // After the commit the entity holds its final @Version (incremented at flush).
        RouteEventPayload payload = switch (change.kind()) {
            case CREATED, UPDATED -> RouteEventPayload.of(route.toSummary(), route.getVersion());
            case DELETED -> RouteEventPayload.deleted(route.getId(), route.getVersion());
        };
        String eventType = switch (change.kind()) {
            case CREATED -> RouteEvents.ROUTE_CREATED;
            case UPDATED -> RouteEvents.ROUTE_UPDATED;
            case DELETED -> RouteEvents.ROUTE_DELETED;
        };
        String routingKey = switch (change.kind()) {
            case CREATED -> RouteEvents.CREATED_KEY;
            case UPDATED -> RouteEvents.UPDATED_KEY;
            case DELETED -> RouteEvents.DELETED_KEY;
        };
        try {
            eventPublisher.publish(eventType, routingKey, route.getId(), payload);
        } catch (AmqpException e) {
            log.error("Route {} was saved but {} could not be published (event lost): {}",
                    route.getId(), eventType, e.getMessage());
        }
    }
}
