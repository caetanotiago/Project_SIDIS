package isep.psoft.aisafe.airports.command;

import isep.sidis.common.messaging.IntegrationEvent;

// Evento à espera do commit
public record OutgoingIntegrationEvent(String routingKey, IntegrationEvent<?> event) {
}
