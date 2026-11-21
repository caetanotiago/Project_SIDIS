package isep.psoft.aisafe.airports.events;

/**
 * Payload de {@code AirportSyncRequested}.
 *
 * @param requestedBy réplica que arrancou e quer receber o estado atual dos aeroportos das outras
 */
public record AirportSyncRequest(String requestedBy) {
}
