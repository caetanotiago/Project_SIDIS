package isep.psoft.aisafe.maintenance.command;

import isep.sidis.common.messaging.IntegrationEvent;

public record OutgoingIntegrationEvent(String routingKey, IntegrationEvent<?> event) {
}
