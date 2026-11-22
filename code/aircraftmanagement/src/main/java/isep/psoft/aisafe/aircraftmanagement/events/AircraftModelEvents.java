package isep.psoft.aisafe.aircraftmanagement.events;

import isep.sidis.common.messaging.MessagingNames;

/**
 * Public contract of the aircraft-model events published by Aircraft Management on
 * {@code aisafe.aircraftmanagement.events} (docs/2-containers/Integration-Contracts.md, section 9.4). Every event
 * carries an {@link AircraftModelEventPayload}.
 *
 * <table>
 *   <caption>Aircraft-model events</caption>
 *   <tr><th>eventType</th><th>routing key</th><th>when</th></tr>
 *   <tr><td>{@code AircraftModelCreated}</td><td>{@code aircraft-model.created}</td>
 *       <td>US101, after the model is stored on the replica that received the request (its owner)</td></tr>
 *   <tr><td>{@code AircraftModelUpdated}</td><td>{@code aircraft-model.updated}</td>
 *       <td>US201 (specifications, e.g. {@code maximumRange}), on the owner replica. Added in Assignment 2 so that the
 *           copies kept by other services (Flight Routes {@code KNOWN_AIRCRAFT_MODEL}) do not go stale.</td></tr>
 * </table>
 */
public final class AircraftModelEvents {

    public static final String AGGREGATE = "aircraft-model";

    public static final String AIRCRAFT_MODEL_CREATED = "AircraftModelCreated";
    public static final String AIRCRAFT_MODEL_UPDATED = "AircraftModelUpdated";

    public static final String CREATED_KEY = MessagingNames.routingKey(AGGREGATE, "created");
    public static final String UPDATED_KEY = MessagingNames.routingKey(AGGREGATE, "updated");

    /** Binding pattern that matches every aircraft-model event. */
    public static final String ALL_AIRCRAFT_MODEL_EVENTS = AGGREGATE + ".*";

    private AircraftModelEvents() {
    }
}
