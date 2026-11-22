package isep.psoft.aisafe.maintenance.command;

public interface IntegrationEventTransport {

    void send(OutgoingIntegrationEvent outgoing);
}
