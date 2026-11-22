package isep.psoft.aisafe.aircraftmanagement.events.external;

/**
 * Events of Flight Routes consumed by Aircraft Management (published on {@code aisafe.flightroutes.events},
 * docs/2-containers/Integration-Contracts.md, section 9.3). They replace the HTTP bridges of Assignment 1:
 *
 * <table>
 *   <caption>Consumed events</caption>
 *   <tr><th>eventType</th><th>routing key</th><th>payload</th><th>read model / replaces</th></tr>
 *   <tr><td>{@code RouteCreated}, {@code RouteUpdated}</td><td>{@code route.*}</td><td>{@link RoutePayload}</td>
 *       <td>{@code KNOWN_ROUTE}: US203 compatible routes (bridge B6)</td></tr>
 *   <tr><td>{@code ScheduledFlightCreated}, {@code ScheduledFlightStatusChanged}</td><td>{@code scheduled-flight.*}</td>
 *       <td>{@link ScheduledFlightPayload}</td><td>{@code KNOWN_SCHEDULED_FLIGHT}: US204 / US206 flight minutes (bridge B4b)</td></tr>
 * </table>
 *
 * The names are repeated here on purpose (not imported from Flight Routes): each service owns its own copy of the
 * published language of the other (anti-corruption layer), and services never share code other than {@code common}.
 */
public final class FlightRoutesEvents {

    public static final String SOURCE_SERVICE = "flightroutes";

    public static final String ROUTE_CREATED = "RouteCreated";
    public static final String ROUTE_UPDATED = "RouteUpdated";
    public static final String SCHEDULED_FLIGHT_CREATED = "ScheduledFlightCreated";
    public static final String SCHEDULED_FLIGHT_STATUS_CHANGED = "ScheduledFlightStatusChanged";

    /** Binding patterns of the queue {@code <instanceId>.flightroutes}. */
    public static final String ALL_ROUTE_EVENTS = "route.*";
    public static final String ALL_SCHEDULED_FLIGHT_EVENTS = "scheduled-flight.*";

    private FlightRoutesEvents() {
    }
}
