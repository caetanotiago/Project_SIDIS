package isep.psoft.aisafe.airports.events;

import isep.sidis.common.messaging.MessagingNames;

public final class AirportEvents {
    public static final String SOURCE_SERVICE = "airports";

    public static final String AGGREGATE = "airport";

    public static final String AIRPORT_CREATED = "AirportCreated";
    public static final String AIRPORT_UPDATED = "AirportUpdated";
    public static final String AIRPORT_STATUS_CHANGED = "AirportStatusChanged";

    public static final String CREATED_KEY = MessagingNames.routingKey(AGGREGATE, "created");
    public static final String UPDATED_KEY = MessagingNames.routingKey(AGGREGATE, "updated");
    public static final String STATUS_CHANGED_KEY = MessagingNames.routingKey(AGGREGATE, "status-changed");

    public static final String SYNC_REQUESTED = "AirportSyncRequested";
    public static final String SYNC_REQUESTED_KEY = MessagingNames.routingKey("airport-sync", "requested");

    public static final String ALL_AIRPORT_EVENTS = AGGREGATE + ".*";

    private AirportEvents() {
    }
}
