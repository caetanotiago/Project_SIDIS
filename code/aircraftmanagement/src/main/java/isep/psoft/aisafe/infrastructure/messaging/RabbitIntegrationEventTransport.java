package isep.psoft.aisafe.infrastructure.messaging;

import isep.psoft.aisafe.aircraftmanagement.command.IntegrationEventTransport;
import isep.psoft.aisafe.aircraftmanagement.command.OutgoingIntegrationEvent;
import isep.sidis.common.messaging.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Publishes committed events to {@code aisafe.aircraftmanagement.events} through the common library's
 * {@link EventPublisher} (persistent messages, publisher confirms). Only with {@code aisafe.messaging.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class RabbitIntegrationEventTransport implements IntegrationEventTransport {

    private final EventPublisher eventPublisher;

    @Override
    public void send(OutgoingIntegrationEvent outgoing) {
        eventPublisher.publish(outgoing.event(), outgoing.routingKey());
    }
}
