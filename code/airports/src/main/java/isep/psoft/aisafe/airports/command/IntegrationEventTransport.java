package isep.psoft.aisafe.airports.command;

// Transporte de eventos (interface)
public interface IntegrationEventTransport {
    void send(OutgoingIntegrationEvent outgoing);
}
