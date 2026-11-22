package isep.psoft.aisafe.flightroutes.events.external;

import isep.sidis.common.messaging.MessagingNames;

/**
 * Events that Flight Routes is READY to consume from other services, as agreed in docs/2-containers/Integration-Contracts.md
 * (section 9.4, "Events expected from the other services"). Nothing is subscribed unless
 * {@code aisafe.flightroutes.external-events.enabled=true}, because Aircraft Management and Airports do not publish
 * them yet; until then B1 and B2 stay synchronous HTTP calls.
 *
 * <table>
 *   <caption>Consumed events</caption>
 *   <tr><th>exchange</th><th>eventType</th><th>routing key</th><th>payload</th></tr>
 *   <tr><td>{@code aisafe.aircraftmanagement.events}</td><td>{@code AircraftRegistered}</td>
 *       <td>{@code aircraft.registered}</td><td>{@link AircraftEventPayload}</td></tr>
 *   <tr><td></td><td>{@code AircraftStatusChanged}</td><td>{@code aircraft.status-changed}</td>
 *       <td>{@link AircraftEventPayload}</td></tr>
 *   <tr><td></td><td>{@code AircraftModelCreated}</td><td>{@code aircraft-model.created}</td>
 *       <td>{@link AircraftModelEventPayload}</td></tr>
 *   <tr><td></td><td>{@code AircraftModelUpdated}</td><td>{@code aircraft-model.updated}</td>
 *       <td>{@link AircraftModelEventPayload}</td></tr>
 *   <tr><td>{@code aisafe.airports.events}</td><td>{@code AirportCreated}</td><td>{@code airport.created}</td>
 *       <td>{@link AirportEventPayload}</td></tr>
 *   <tr><td></td><td>{@code AirportUpdated}</td><td>{@code airport.updated}</td><td>{@link AirportEventPayload}</td></tr>
 *   <tr><td></td><td>{@code AirportStatusChanged}</td><td>{@code airport.status-changed}</td>
 *       <td>{@link AirportEventPayload}</td></tr>
 * </table>
 */
public final class ExternalEvents {

    public static final String AIRCRAFT_SERVICE = "aircraftmanagement";
    public static final String AIRPORTS_SERVICE = "airports";

    public static final String AIRCRAFT_REGISTERED = "AircraftRegistered";
    public static final String AIRCRAFT_STATUS_CHANGED = "AircraftStatusChanged";
    public static final String AIRCRAFT_MODEL_CREATED = "AircraftModelCreated";
    /** US201 (specifications changed, e.g. maximumRange); added by Aircraft Management in Assignment 2. */
    public static final String AIRCRAFT_MODEL_UPDATED = "AircraftModelUpdated";

    public static final String AIRPORT_CREATED = "AirportCreated";
    public static final String AIRPORT_UPDATED = "AirportUpdated";
    public static final String AIRPORT_STATUS_CHANGED = "AirportStatusChanged";

    public static final String AIRCRAFT_REGISTERED_KEY = MessagingNames.routingKey("aircraft", "registered");
    public static final String AIRCRAFT_STATUS_CHANGED_KEY = MessagingNames.routingKey("aircraft", "status-changed");
    public static final String AIRCRAFT_MODEL_CREATED_KEY = MessagingNames.routingKey("aircraft-model", "created");
    public static final String AIRCRAFT_MODEL_UPDATED_KEY = MessagingNames.routingKey("aircraft-model", "updated");
    public static final String AIRPORT_CREATED_KEY = MessagingNames.routingKey("airport", "created");
    public static final String AIRPORT_UPDATED_KEY = MessagingNames.routingKey("airport", "updated");
    public static final String AIRPORT_STATUS_CHANGED_KEY = MessagingNames.routingKey("airport", "status-changed");

    private ExternalEvents() {
    }
}
