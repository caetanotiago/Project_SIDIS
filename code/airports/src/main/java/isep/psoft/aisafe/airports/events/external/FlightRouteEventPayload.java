package isep.psoft.aisafe.airports.events.external;

public record FlightRouteEventPayload(String routeId, String originIata, String destinationIata, String status,
                                      Long version) {
}
