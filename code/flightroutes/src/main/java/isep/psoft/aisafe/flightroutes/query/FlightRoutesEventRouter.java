package isep.psoft.aisafe.flightroutes.query;

import isep.psoft.aisafe.flightroutes.events.RouteEventPayload;
import isep.psoft.aisafe.flightroutes.events.RouteEvents;
import isep.psoft.aisafe.flightroutes.events.ScheduledFlightEventPayload;
import isep.psoft.aisafe.flightroutes.events.ScheduledFlightEvents;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.IntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entry point of the query side for Flight Routes' OWN events: routes each event, by {@code eventType}, to the
 * projection that owns that read model. Used by
 * <ul>
 *   <li>the command side, which applies its own event to the local read model inside the write transaction;</li>
 *   <li>{@link FlightRoutesEventsListener}, for the events that arrive from RabbitMQ (the payload is then a JSON
 *       tree, because one queue carries several payload types, and it is converted here);</li>
 *   <li>tests, which deliver events between replicas without a broker.</li>
 * </ul>
 */
@Component
public class FlightRoutesEventRouter {

    private static final Logger log = LoggerFactory.getLogger(FlightRoutesEventRouter.class);
    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();

    private final RouteProjection routes;
    private final ScheduledFlightProjection flights;

    public FlightRoutesEventRouter(RouteProjection routes, ScheduledFlightProjection flights) {
        this.routes = routes;
        this.flights = flights;
    }

    /** @return {@code true} if the event was applied, {@code false} if it was a duplicate or of an unknown type */
    public boolean apply(IntegrationEvent<?> event) {
        return switch (event.eventType()) {
            case RouteEvents.ROUTE_CREATED, RouteEvents.ROUTE_UPDATED ->
                    routes.apply(withPayload(event, RouteEventPayload.class));
            case ScheduledFlightEvents.SCHEDULED_FLIGHT_CREATED, ScheduledFlightEvents.SCHEDULED_FLIGHT_STATUS_CHANGED ->
                    flights.apply(withPayload(event, ScheduledFlightEventPayload.class));
            default -> {
                log.warn("Ignoring unknown event type {} ({}) from {}", event.eventType(), event.eventId(),
                        event.sourceInstance());
                yield false;
            }
        };
    }

    /** Same envelope with the payload as {@code type} (converted from JSON when it came from the broker). */
    static <T> IntegrationEvent<T> withPayload(IntegrationEvent<?> event, Class<T> type) {
        Object raw = event.payload();
        T payload = raw == null || type.isInstance(raw) ? type.cast(raw) : JSON.convertValue(raw, type);
        return new IntegrationEvent<>(event.eventId(), event.eventType(), event.timestamp(), event.sourceService(),
                event.sourceInstance(), event.aggregateId(), payload);
    }
}
