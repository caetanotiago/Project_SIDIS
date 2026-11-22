package isep.psoft.aisafe.maintenance.events.external;

public final class FlightRoutesEvents {

    public static final String SOURCE_SERVICE = "flightroutes";

    public static final String SCHEDULED_FLIGHT_CREATED = "ScheduledFlightCreated";
    public static final String SCHEDULED_FLIGHT_STATUS_CHANGED = "ScheduledFlightStatusChanged";

    public static final String ALL_SCHEDULED_FLIGHT_EVENTS = "scheduled-flight.*";

    private FlightRoutesEvents() {
    }
}
