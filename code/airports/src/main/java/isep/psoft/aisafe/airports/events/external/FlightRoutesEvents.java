package isep.psoft.aisafe.airports.events.external;

/** Eventos do Flight Routes consumidos pelo Airports (contrato: Integration-Contracts.md, secção 9.3). */
public final class FlightRoutesEvents {

    /** Serviço produtor: exchange {@code aisafe.flightroutes.events}; fila desta réplica {@code <instanceId>.flightroutes}. */
    public static final String SOURCE_SERVICE = "flightroutes";

    public static final String ROUTE_CREATED = "RouteCreated";
    public static final String ROUTE_UPDATED = "RouteUpdated";

    /** Só os eventos de rotas (os de voos agendados não interessam ao Airports). */
    public static final String ALL_ROUTE_EVENTS = "route.*";

    private FlightRoutesEvents() {
    }
}
