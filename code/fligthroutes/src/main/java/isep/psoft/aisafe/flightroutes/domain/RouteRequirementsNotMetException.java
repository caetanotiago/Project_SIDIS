package isep.psoft.aisafe.flightroutes.domain;

/**
 * Thrown (US212) when an aircraft does not comply with a route's requirements
 * (insufficient range or seating capacity). Mapped to HTTP 422 Unprocessable Entity.
 */
public class RouteRequirementsNotMetException extends RuntimeException {
    public RouteRequirementsNotMetException(String message) {
        super(message);
    }
}
