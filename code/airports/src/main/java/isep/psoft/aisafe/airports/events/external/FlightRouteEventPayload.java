package isep.psoft.aisafe.airports.events.external;

/**
 * Parte do payload de {@code RouteCreated} / {@code RouteUpdated} (publicados pelo Flight Routes em
 * {@code aisafe.flightroutes.events}) de que o Airports precisa. Os restantes campos do evento são ignorados
 * (tolerant reader). Contrato: docs/2-containers/Integration-Contracts.md, secção 9.3.
 *
 * @param status  ACTIVE ou INACTIVE (as rotas inativas também contam na US210, decisão Q6 do grupo)
 * @param version cresce a cada alteração da rota (eventos mais antigos são ignorados)
 */
public record FlightRouteEventPayload(String routeId, String originIata, String destinationIata, String status,
                                      Long version) {
}
