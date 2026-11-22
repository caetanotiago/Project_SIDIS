package isep.psoft.aisafe.airports.command;

public interface IntegrationEventTransport {
    void send(OutgoingIntegrationEvent outgoing);
}
