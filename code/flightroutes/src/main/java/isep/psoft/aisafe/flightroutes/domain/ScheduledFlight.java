package isep.psoft.aisafe.flightroutes.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Aggregate Root (US212): an Aircraft assigned to a FlightRoute for a specific date/time
 * (FlightSchedule), starting in the SCHEDULED status.
 *
 * <p>Both the Aircraft (another microservice) and the FlightRoute (another aggregate, possibly stored
 * in another replica) are referenced by identity. {@link RouteReference} keeps a copy of the route
 * data needed by this aggregate, so no cross-replica JOIN is required.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Exigido pelo JPA
public class ScheduledFlight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // Reference to the Aircraft aggregate by identity (registration number).
    @Column(name = "aircraft_registration", nullable = false)
    private String aircraftRegistration;

    // Reference to the FlightRoute aggregate by identity (+ snapshot of the route data).
    @Embedded
    private RouteReference route;

    @Embedded
    private FlightSchedule schedule;

    @Embedded
    private FlightStatus status;

    @Version
    private Long version; // Optimistic Locking (impede double-booking concorrente)

    public ScheduledFlight(String aircraftRegistration, RouteReference route, FlightSchedule schedule) {
        if (aircraftRegistration == null || aircraftRegistration.isBlank()) {
            throw new IllegalArgumentException("Aircraft registration is required.");
        }
        if (route == null) {
            throw new IllegalArgumentException("Route is required.");
        }
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule is required.");
        }
        this.aircraftRegistration = aircraftRegistration;
        this.route = route;
        this.schedule = schedule;
        this.status = FlightStatus.scheduled(); // nasce SCHEDULED
    }
}
