package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.events.AircraftEventPayload;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftEvents;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftModelEventPayload;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftModelEvents;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.IntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entry point of the query side for Aircraft Management's OWN events: routes each event, by {@code eventType}, to the
 * projection that owns that read model (as {@code FlightRoutesEventRouter} in Flight Routes). Used by
 * <ul>
 *   <li>the command side, which applies its own event to the local read model inside the write transaction;</li>
 *   <li>{@link AircraftManagementEventsListener}, for the events that arrive from RabbitMQ (the payload is then a JSON
 *       tree, because one queue carries two payload types, and it is converted here);</li>
 *   <li>tests, which deliver events between replicas without a broker.</li>
 * </ul>
 */
@Component
public class AircraftManagementEventRouter {

    private static final Logger log = LoggerFactory.getLogger(AircraftManagementEventRouter.class);
    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();

    private final AircraftProjection aircraft;
    private final AircraftModelProjection models;

    public AircraftManagementEventRouter(AircraftProjection aircraft, AircraftModelProjection models) {
        this.aircraft = aircraft;
        this.models = models;
    }

    /** @return {@code true} if the event was applied, {@code false} if it was a duplicate or of an unknown type */
    public boolean apply(IntegrationEvent<?> event) {
        return switch (event.eventType()) {
            case AircraftEvents.AIRCRAFT_REGISTERED, AircraftEvents.AIRCRAFT_STATUS_CHANGED ->
                    aircraft.apply(withPayload(event, AircraftEventPayload.class));
            case AircraftModelEvents.AIRCRAFT_MODEL_CREATED, AircraftModelEvents.AIRCRAFT_MODEL_UPDATED ->
                    models.apply(withPayload(event, AircraftModelEventPayload.class));
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
