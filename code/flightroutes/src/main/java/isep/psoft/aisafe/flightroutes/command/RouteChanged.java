package isep.psoft.aisafe.flightroutes.command;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;

/**
 * In-process (Spring) event raised by the write side when a route stored in THIS replica changes.
 * It is turned into an integration event only after the transaction commits ({@link RouteIntegrationEventPublisher}),
 * so the broker never sees a change that was rolled back. Without messaging nobody listens and it is a no-op.
 */
public record RouteChanged(Kind kind, FlightRoute route) {

    public enum Kind { CREATED, UPDATED, DELETED }

    public static RouteChanged created(FlightRoute route) {
        return new RouteChanged(Kind.CREATED, route);
    }

    public static RouteChanged updated(FlightRoute route) {
        return new RouteChanged(Kind.UPDATED, route);
    }
}
