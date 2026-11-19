package isep.psoft.aisafe.flightroutes.query.external;

import isep.psoft.aisafe.flightroutes.events.external.AircraftModelEventPayload;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Local read model of an aircraft model of Aircraft Management (replaces B1b once enabled). */
@Entity
@Table(name = "KNOWN_AIRCRAFT_MODEL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnownAircraftModel {

    @Id
    private String modelName;
    private Double maximumRange;
    private Long sourceVersion;

    KnownAircraftModel(String modelName) {
        this.modelName = modelName;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(AircraftModelEventPayload p) {
        if (p.maximumRange() != null) this.maximumRange = p.maximumRange();
        this.sourceVersion = p.version();
    }
}
