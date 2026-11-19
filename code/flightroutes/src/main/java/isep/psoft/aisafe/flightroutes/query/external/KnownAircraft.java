package isep.psoft.aisafe.flightroutes.query.external;

import isep.psoft.aisafe.flightroutes.events.external.AircraftEventPayload;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Local read model of an aircraft of Aircraft Management, built from its events (replaces B1a once enabled). */
@Entity
@Table(name = "KNOWN_AIRCRAFT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnownAircraft {

    @Id
    private String registrationNumber;
    private String modelName;
    private Integer seatingCapacity;
    private String status;
    private Long sourceVersion;

    KnownAircraft(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    /** Fields that are null in the payload (e.g. a status-only event) keep their current value. */
    void apply(AircraftEventPayload p) {
        if (p.modelName() != null) this.modelName = p.modelName();
        if (p.seatingCapacity() != null) this.seatingCapacity = p.seatingCapacity();
        if (p.status() != null) this.status = p.status();
        this.sourceVersion = p.version();
    }
}
