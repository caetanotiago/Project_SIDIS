package isep.psoft.aisafe.maintenance.query.external;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "KNOWN_AIRCRAFT_MODEL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnownAircraftModel {

    @Id
    private String modelName;

    private String manufacturer;
    private Long sourceVersion;
    private String source;
    private Instant updatedAt;

    KnownAircraftModel(String modelName) {
        this.modelName = modelName;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(String manufacturer, Long version, String source, Instant at) {
        if (manufacturer != null) this.manufacturer = manufacturer;
        this.sourceVersion = version;
        this.source = source;
        this.updatedAt = at;
    }
}
