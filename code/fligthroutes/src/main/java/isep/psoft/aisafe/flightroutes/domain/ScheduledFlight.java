package isep.psoft.aisafe.flightroutes.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Aggregate Root of the Flight Route & Schedule Aggregate (US212).
 * Combines a FlightRoute (same aggregate, direct association) with an Aircraft
 * (referenced by identity — DDD: other aggregates are referenced by id) for a
 * specific date/time (FlightSchedule), starting in the SCHEDULED status.
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

    // Same aggregate (Flight Route & Schedule): direct association.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private FlightRoute route;

    @Embedded
    private FlightSchedule schedule;

    @Embedded
    private FlightStatus status;

    @Version
    private Long version; // Optimistic Locking (impede double-booking concorrente)

    public ScheduledFlight(String aircraftRegistration, FlightRoute route, FlightSchedule schedule) {
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
