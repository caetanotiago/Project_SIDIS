package isep.psoft.aisafe.aircraftmanagement.events;

import isep.sidis.common.messaging.MessagingNames;

/**
 * Public contract of the aircraft events published by Aircraft Management on {@code aisafe.aircraftmanagement.events}
 * (docs/2-containers/Integration-Contracts.md, section 9.4). Every event carries an {@link AircraftEventPayload}.
 *
 * <table>
 *   <caption>Aircraft events</caption>
 *   <tr><th>eventType</th><th>routing key</th><th>when</th></tr>
 *   <tr><td>{@code AircraftRegistered}</td><td>{@code aircraft.registered}</td>
 *       <td>US102, after the aircraft is stored on the replica that received the request (its owner)</td></tr>
 *   <tr><td>{@code AircraftStatusChanged}</td><td>{@code aircraft.status-changed}</td>
 *       <td>US105 (including the change requested by Maintenance, B3b), on the owner, only if the status changed</td></tr>
 * </table>
 *
 * <p>There is no {@code AircraftDeleted}: aircraft are never deleted ({@code INACTIVE} is a status).
 */
public final class AircraftEvents {

    /** Producing service: the exchange is {@code aisafe.aircraftmanagement.events}. */
    public static final String SOURCE_SERVICE = "aircraftmanagement";

    public static final String AGGREGATE = "aircraft";

    public static final String AIRCRAFT_REGISTERED = "AircraftRegistered";
    public static final String AIRCRAFT_STATUS_CHANGED = "AircraftStatusChanged";

    public static final String REGISTERED_KEY = MessagingNames.routingKey(AGGREGATE, "registered");
    public static final String STATUS_CHANGED_KEY = MessagingNames.routingKey(AGGREGATE, "status-changed");

    /**
     * Synchronisation request: a replica that just started asks the others to republish the state they own (see
     * {@code ReadModelSynchronizer}). It uses another "aggregate" ({@code aircraft-sync}) so that it does NOT match
     * the binding {@code aircraft.*}: only the Aircraft Management replicas receive it, other services do not.
     */
    public static final String SYNC_REQUESTED = "AircraftSyncRequested";
    public static final String SYNC_REQUESTED_KEY = MessagingNames.routingKey("aircraft-sync", "requested");

    /** Binding pattern that matches every aircraft event. */
    public static final String ALL_AIRCRAFT_EVENTS = AGGREGATE + ".*";

    private AircraftEvents() {
    }
}
