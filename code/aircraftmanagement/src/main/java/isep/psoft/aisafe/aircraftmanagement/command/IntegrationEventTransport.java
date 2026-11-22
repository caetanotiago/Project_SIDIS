package isep.psoft.aisafe.aircraftmanagement.command;

/**
 * Sends a committed integration event to the other replicas/services. Production: RabbitMQ
 * ({@code RabbitIntegrationEventTransport}, only with {@code aisafe.messaging.enabled=true}). Tests can register
 * an in-process transport that delivers the event to other replicas started in the same JVM.
 * With no transport (messaging off), events only update this replica's own read model.
 */
public interface IntegrationEventTransport {

    void send(OutgoingIntegrationEvent outgoing);
}
