package isep.psoft.aisafe.flightroutes.domain;

/**
 * Read Model (imutável, não persistido): vista de uma FlightRoute independente da réplica onde está
 * guardada. As consultas que agregam rotas de várias réplicas (US113, US114, US203, US209, US214,
 * US215, US216) trabalham sobre esta vista: as rotas locais são convertidas com
 * {@link FlightRoute#toSummary()} e as recebidas dos peers a partir do FlightRouteDTO.
 */
public record RouteSummary(String id, String originIata, String destinationIata, Double distance,
                           Double minRange, Integer minCapacity, Integer estimatedFlightTime, String status) {

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    /** Information Expert: os requisitos da rota são cumpridos por uma aeronave com este alcance/capacidade? */
    public boolean isMetBy(double aircraftRange, int aircraftCapacity) {
        return new RouteRequirements(minRange, minCapacity).isMetBy(aircraftRange, aircraftCapacity);
    }
}
