package isep.psoft.aisafe.infrastructure.messaging;

import isep.psoft.aisafe.airports.command.ReadModelSynchronizer;
import isep.psoft.aisafe.airports.events.AirportEventPayload;
import isep.psoft.aisafe.airports.events.AirportEvents;
import isep.psoft.aisafe.airports.events.AirportSyncRequest;
import isep.psoft.aisafe.airports.query.AirportProjection;
import isep.sidis.common.messaging.EventMessageConverters;
import isep.sidis.common.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Lê fila <id>.airports
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AirportsEventsListener {
    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();

    private final AirportProjection projection;
    private final ReadModelSynchronizer synchronizer;

    // Encaminha pelo tipo
    @RabbitListener(id = "airports-events", queues = "#{airportEventsSubscription.queueName}")
    public void on(IntegrationEvent<JsonNode> event) {
        if (AirportEvents.SYNC_REQUESTED.equals(event.eventType())) {
            AirportSyncRequest request = JSON.convertValue(event.payload(), AirportSyncRequest.class);
            synchronizer.onSyncRequested(request == null ? event.sourceInstance() : request.requestedBy());
            return;
        }
        projection.apply(withPayload(event, AirportEventPayload.class));
    }

    // JSON → payload
    static <T> IntegrationEvent<T> withPayload(IntegrationEvent<JsonNode> event, Class<T> type) {
        T payload = event.payload() == null ? null : JSON.convertValue(event.payload(), type);
        return new IntegrationEvent<>(event.eventId(), event.eventType(), event.timestamp(), event.sourceService(),
                event.sourceInstance(), event.aggregateId(), payload);
    }
}
