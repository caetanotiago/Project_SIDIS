package isep.psoft.aisafe.airports.events.external;

// Eventos do Flight Routes
public final class FlightRoutesEvents {
    public static final String SOURCE_SERVICE = "flightroutes";

    public static final String ROUTE_CREATED = "RouteCreated";
    public static final String ROUTE_UPDATED = "RouteUpdated";

    public static final String ALL_ROUTE_EVENTS = "route.*";

    private FlightRoutesEvents() {
    }
}
