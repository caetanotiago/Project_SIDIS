package isep.psoft.aisafe.flightroutes.events;

import isep.sidis.common.messaging.MessagingNames;

/**
 * Public contract of the route events published by Flight Routes on {@code aisafe.flightroutes.events}
 * (docs/2-containers/Integration-Contracts.md, section 9.3). Every event carries a {@link RouteEventPayload}.
 *
 * <table>
 *   <caption>Route events</caption>
 *   <tr><th>eventType</th><th>routing key</th><th>when</th></tr>
 *   <tr><td>{@code RouteCreated}</td><td>{@code route.created}</td><td>US110, after the route is stored</td></tr>
 *   <tr><td>{@code RouteUpdated}</td><td>{@code route.updated}</td>
 *       <td>US112 (details or ACTIVE/INACTIVE), on the owner replica, after the update is stored</td></tr>
 * </table>
 *
 * <p>There is no {@code RouteDeleted}: routes are never deleted. Deactivating a route (US112) is a
 * {@code RouteUpdated} whose payload has {@code status = "INACTIVE"}.
 */
public final class RouteEvents {

    /** Producing service: the exchange is {@code aisafe.flightroutes.events}. */
    public static final String SOURCE_SERVICE = "flightroutes";

    public static final String AGGREGATE = "route";

    public static final String ROUTE_CREATED = "RouteCreated";
    public static final String ROUTE_UPDATED = "RouteUpdated";

    public static final String CREATED_KEY = MessagingNames.routingKey(AGGREGATE, "created");
    public static final String UPDATED_KEY = MessagingNames.routingKey(AGGREGATE, "updated");

    /** Binding pattern that matches every route event. */
    public static final String ALL_ROUTE_EVENTS = AGGREGATE + ".*";

    private RouteEvents() {
    }
}
