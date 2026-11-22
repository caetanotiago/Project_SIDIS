package isep.psoft.aisafe.aircraftmanagement.query.external;

import isep.psoft.aisafe.aircraftmanagement.events.external.ScheduledFlightPayload;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Local read model of a scheduled flight of Flight Routes (table {@code KNOWN_SCHEDULED_FLIGHT}), built from its
 * {@code ScheduledFlightCreated} / {@code ScheduledFlightStatusChanged} events. Only what the statistics need: the
 * aircraft and the flight minutes. Answers US204 and US206 without calling Flight Routes (replaces bridge B4b).
 */
@Entity
@Table(name = "KNOWN_SCHEDULED_FLIGHT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnownScheduledFlight {

    @Id
    private String flightId;

    private String aircraftRegistration;
    private String routeId;
    /** Minutes, copied by Flight Routes from the route when the flight was scheduled. */
    private Integer estimatedFlightTime;
    private String status;

    private Long sourceVersion;
    private Instant lastEventAt;

    KnownScheduledFlight(String flightId) {
        this.flightId = flightId;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(ScheduledFlightPayload p, Instant eventAt) {
        if (p.aircraftRegistration() != null) this.aircraftRegistration = p.aircraftRegistration();
        if (p.routeId() != null) this.routeId = p.routeId();
        if (p.estimatedFlightTime() != null) this.estimatedFlightTime = p.estimatedFlightTime();
        if (p.status() != null) this.status = p.status();
        this.sourceVersion = p.version();
        this.lastEventAt = eventAt;
    }
}
