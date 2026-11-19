package isep.psoft.aisafe.flightroutes.command;

import isep.sidis.common.messaging.IntegrationEvent;

/**
 * In-process (Spring) event raised by the command side inside the write transaction. {@link IntegrationEventRelay}
 * hands it to the transports (RabbitMQ in production) only AFTER the commit, so the broker never sees a change that
 * was rolled back.
 */
public record OutgoingIntegrationEvent(String routingKey, IntegrationEvent<?> event) {
}
