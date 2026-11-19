package isep.psoft.aisafe.flightroutes.events;

import isep.sidis.common.messaging.MessagingNames;

/**
 * Public contract of the scheduled-flight events published by Flight Routes on {@code aisafe.flightroutes.events}
 * (docs/2-containers/Integration-Contracts.md, section 9.3). Every event carries a {@link ScheduledFlightEventPayload}.
 *
 * <table>
 *   <caption>Scheduled-flight events</caption>
 *   <tr><th>eventType</th><th>routing key</th><th>when</th></tr>
 *   <tr><td>{@code ScheduledFlightCreated}</td><td>{@code scheduled-flight.created}</td>
 *       <td>US212, after the flight is stored on the replica that received the request (its owner)</td></tr>
 *   <tr><td>{@code ScheduledFlightStatusChanged}</td><td>{@code scheduled-flight.status-changed}</td>
 *       <td>reserved: the API cannot change a flight's status yet (flights are born SCHEDULED). The read model
 *           already applies it, so the producer can be added without touching any consumer.</td></tr>
 * </table>
 */
public final class ScheduledFlightEvents {

    public static final String AGGREGATE = "scheduled-flight";

    public static final String SCHEDULED_FLIGHT_CREATED = "ScheduledFlightCreated";
    public static final String SCHEDULED_FLIGHT_STATUS_CHANGED = "ScheduledFlightStatusChanged";

    public static final String CREATED_KEY = MessagingNames.routingKey(AGGREGATE, "created");
    public static final String STATUS_CHANGED_KEY = MessagingNames.routingKey(AGGREGATE, "status-changed");

    /** Binding pattern that matches every scheduled-flight event. */
    public static final String ALL_SCHEDULED_FLIGHT_EVENTS = AGGREGATE + ".*";

    private ScheduledFlightEvents() {
    }
}
