package isep.psoft.aisafe.airports.events.external;

// Evento de rota recebido
public record FlightRouteEventPayload(String routeId, String originIata, String destinationIata, String status,
                                      Long version) {
}
