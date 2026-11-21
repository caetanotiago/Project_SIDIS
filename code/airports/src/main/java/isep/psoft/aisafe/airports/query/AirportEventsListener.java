package isep.psoft.aisafe.airports.query;

import isep.psoft.aisafe.airports.events.AirportEventPayload;
import isep.sidis.common.messaging.IntegrationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consome os eventos de aeroportos da fila DESTA réplica, {@code <instanceId>.airports} (declarada em
 * {@code AirportsMessagingConfiguration}), e entrega-os ao {@link AirportProjection}.
 *
 * <p>A mensagem só é confirmada (ack) quando este método termina; uma exceção faz o container repetir (com
 * back-off, número limitado de tentativas) e depois envia-a para {@code <fila>.dlq} (política da common).
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AirportEventsListener {

    private final AirportProjection projection;

    @RabbitListener(id = "airports-events", queues = "#{airportEventsSubscription.queueName}")
    public void on(IntegrationEvent<AirportEventPayload> event) {
        projection.apply(event);
    }
}
