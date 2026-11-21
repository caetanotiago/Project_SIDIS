package isep.psoft.aisafe.infrastructure.messaging;

import isep.psoft.aisafe.airports.command.IntegrationEventTransport;
import isep.psoft.aisafe.airports.command.OutgoingIntegrationEvent;
import isep.sidis.common.messaging.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Publica os eventos já gravados no exchange {@code aisafe.airports.events} através do {@link EventPublisher} da
 * biblioteca common (mensagens persistentes, publisher confirms). Só com {@code aisafe.messaging.enabled=true}.
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
