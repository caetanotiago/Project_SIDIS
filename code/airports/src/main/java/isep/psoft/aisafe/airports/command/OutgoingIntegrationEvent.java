package isep.psoft.aisafe.airports.command;

import isep.sidis.common.messaging.IntegrationEvent;

public record OutgoingIntegrationEvent(String routingKey, IntegrationEvent<?> event) {
}
