package isep.psoft.aisafe.maintenance.query.external;

import isep.psoft.aisafe.maintenance.events.external.ScheduledFlightPayload;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "KNOWN_SCHEDULED_FLIGHT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnownScheduledFlight {

    @Id
    private String flightId;

    private String aircraftRegistration;
    private Integer estimatedFlightTime;
    private String status;
    private Long sourceVersion;
    private Instant updatedAt;

    KnownScheduledFlight(String flightId) {
        this.flightId = flightId;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(ScheduledFlightPayload p, Instant at) {
        if (p.aircraftRegistration() != null) this.aircraftRegistration = p.aircraftRegistration();
        if (p.estimatedFlightTime() != null) this.estimatedFlightTime = p.estimatedFlightTime();
        if (p.status() != null) this.status = p.status();
        this.sourceVersion = p.version();
        this.updatedAt = at;
    }
}
