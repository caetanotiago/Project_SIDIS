package isep.psoft.aisafe.aircraftmanagement.events.external;

/**
 * The fields of Flight Routes' {@code RouteEventPayload} that Aircraft Management reads (tolerant reader: the others,
 * e.g. {@code history}, are ignored).
 *
 * <pre>
 * { "routeId": "8c1d...", "originIata": "OPO", "destinationIata": "LIS", "distance": 274.0, "minRange": 400.0,
 *   "minCapacity": 120, "estimatedFlightTime": 55, "status": "ACTIVE", "version": 0, "history": [ ... ] }
 * </pre>
 *
 * @param minRange route requirement in km, compared with the model's {@code maximumRange}
 * @param status   ACTIVE or INACTIVE (routes are never deleted)
 */
public record RoutePayload(String routeId, String originIata, String destinationIata, Double distance,
                           Double minRange, Integer minCapacity, Integer estimatedFlightTime, String status,
                           Long version) {
}
