package isep.psoft.aisafe.flightroutes.domain;

/**
 * Read projection (US214): pairs an active route with its usage count (number of ScheduledFlight
 * created on it = route popularity), summed across all replicas.
 */
public record RouteUsage(RouteSummary route, long usageCount) {
}
