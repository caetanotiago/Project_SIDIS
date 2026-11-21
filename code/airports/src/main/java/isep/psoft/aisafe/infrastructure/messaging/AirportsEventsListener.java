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

/**
 * Consome a fila DESTA réplica, {@code <instanceId>.airports} (declarada em {@code AirportsMessagingConfiguration}).
 * A fila traz dois tipos de mensagem, por isso o payload é lido como árvore JSON e convertido conforme o
 * {@code eventType}:
 * <ul>
 *   <li>eventos de aeroportos → {@link AirportProjection} (read model {@code REPLICATED_AIRPORT});</li>
 *   <li>{@code AirportSyncRequested} → {@link ReadModelSynchronizer} (volta a publicar os aeroportos desta réplica).</li>
 * </ul>
 * A mensagem só é confirmada (ack) quando este método termina; uma exceção faz o container repetir (com back-off,
 * número limitado de tentativas) e depois envia-a para {@code <fila>.dlq} (política da common).
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AirportsEventsListener {

    private static final JsonMapper JSON = EventMessageConverters.jsonMapper();

    private final AirportProjection projection;
    private final ReadModelSynchronizer synchronizer;

    @RabbitListener(id = "airports-events", queues = "#{airportEventsSubscription.queueName}")
    public void on(IntegrationEvent<JsonNode> event) {
        if (AirportEvents.SYNC_REQUESTED.equals(event.eventType())) {
            AirportSyncRequest request = JSON.convertValue(event.payload(), AirportSyncRequest.class);
            synchronizer.onSyncRequested(request == null ? event.sourceInstance() : request.requestedBy());
            return;
        }
        projection.apply(withPayload(event, AirportEventPayload.class));
    }

    /** O mesmo envelope com o payload convertido para {@code type}. */
    static <T> IntegrationEvent<T> withPayload(IntegrationEvent<JsonNode> event, Class<T> type) {
        T payload = event.payload() == null ? null : JSON.convertValue(event.payload(), type);
        return new IntegrationEvent<>(event.eventId(), event.eventType(), event.timestamp(), event.sourceService(),
                event.sourceInstance(), event.aggregateId(), payload);
    }
}
