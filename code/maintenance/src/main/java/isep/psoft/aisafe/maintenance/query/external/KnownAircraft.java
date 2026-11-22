package isep.psoft.aisafe.maintenance.query.external;

import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.psoft.aisafe.maintenance.events.external.AircraftPayload;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "KNOWN_AIRCRAFT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnownAircraft {

    public static final String FROM_EVENT = "event";
    public static final String FROM_HTTP = "http";

    @Id
    private String registrationNumber;

    private String modelName;
    private LocalDate manufacturingDate;
    private String status;

    private Long sourceVersion;
    private String source;
    private Instant updatedAt;

    KnownAircraft(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(AircraftPayload p, Instant at) {
        if (p.modelName() != null) this.modelName = p.modelName();
        if (p.manufacturingDate() != null) this.manufacturingDate = p.manufacturingDate();
        if (p.status() != null) this.status = p.status();
        this.sourceVersion = p.version();
        this.source = FROM_EVENT;
        this.updatedAt = at;
    }

    static KnownAircraft fromHttp(AircraftSnapshot snapshot, Instant at) {
        KnownAircraft row = new KnownAircraft(snapshot.registrationNumber());
        row.modelName = snapshot.modelName();
        row.manufacturingDate = snapshot.manufacturingDate();
        row.status = snapshot.status();
        row.source = FROM_HTTP;
        row.updatedAt = at;
        return row;
    }

    public AircraftSnapshot toSnapshot() {
        return new AircraftSnapshot(registrationNumber, modelName, manufacturingDate, status);
    }
}
