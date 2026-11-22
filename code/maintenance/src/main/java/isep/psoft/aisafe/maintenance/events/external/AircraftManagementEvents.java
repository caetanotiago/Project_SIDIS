package isep.psoft.aisafe.maintenance.events.external;

public final class AircraftManagementEvents {

    public static final String SOURCE_SERVICE = "aircraftmanagement";

    public static final String AIRCRAFT_REGISTERED = "AircraftRegistered";
    public static final String AIRCRAFT_STATUS_CHANGED = "AircraftStatusChanged";
    public static final String AIRCRAFT_MODEL_CREATED = "AircraftModelCreated";
    public static final String AIRCRAFT_MODEL_UPDATED = "AircraftModelUpdated";

    public static final String ALL_AIRCRAFT_EVENTS = "aircraft.*";
    public static final String ALL_AIRCRAFT_MODEL_EVENTS = "aircraft-model.*";

    private AircraftManagementEvents() {
    }
}
