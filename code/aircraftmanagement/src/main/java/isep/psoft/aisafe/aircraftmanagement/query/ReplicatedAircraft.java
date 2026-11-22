package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftEventPayload;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * READ MODEL of an aircraft (table {@code REPLICATED_AIRCRAFT} in this replica's own database), built ONLY from
 * aircraft events. Every replica holds every aircraft here, whichever replica owns it, so every GET of aircraft is
 * answered locally (CQRS query side). The write model ({@code Aircraft}) stays partitioned: each aircraft is stored
 * and updated only by its owner replica.
 *
 * <p>On the owner replica the row is written in the same transaction as the write model (read-your-writes); on the
 * other replicas it is written when the event arrives (eventual consistency).
 */
@Entity
@Table(name = "REPLICATED_AIRCRAFT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicatedAircraft {

    @Id
    private String registrationNumber;

    private String modelName;
    private Integer seatingCapacity;
    private LocalDate manufacturingDate;
    private String status;

    /** Version of the aircraft on its owner replica (from the event), NOT a local optimistic lock. */
    private Long sourceVersion;

    /** Replica that owns (stores and updates) the aircraft: the {@code sourceInstance} of its events. */
    private String ownerInstance;

    /** Id and creation time of the last event applied to this row. */
    private String lastEventId;
    private Instant lastEventAt;

    ReplicatedAircraft(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    /** {@code true} if an event with this source version must be ignored because this row is already newer. */
    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    /** Fields that are null in the payload keep their current value (tolerant to partial status events). */
    void apply(AircraftEventPayload p, String ownerInstance, String eventId, Instant eventAt) {
        if (p.modelName() != null) this.modelName = p.modelName();
        if (p.seatingCapacity() != null) this.seatingCapacity = p.seatingCapacity();
        if (p.manufacturingDate() != null) this.manufacturingDate = p.manufacturingDate();
        if (p.status() != null) this.status = p.status();
        this.sourceVersion = p.version();
        this.ownerInstance = ownerInstance;
        this.lastEventId = eventId;
        this.lastEventAt = eventAt;
    }

    public AircraftSummary toSummary() {
        return new AircraftSummary(registrationNumber, modelName, seatingCapacity == null ? 0 : seatingCapacity,
                manufacturingDate, AircraftStatus.parse(status));
    }
}
