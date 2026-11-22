package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModelSummary;
import isep.psoft.aisafe.aircraftmanagement.events.AircraftModelEventPayload;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * READ MODEL of an aircraft model (table {@code REPLICATED_AIRCRAFT_MODEL}), built ONLY from aircraft-model events,
 * exactly like {@link ReplicatedAircraft}: every replica holds the models of all replicas.
 */
@Entity
@Table(name = "REPLICATED_AIRCRAFT_MODEL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicatedAircraftModel {

    @Id
    private String modelName;

    private String manufacturer;
    private Integer seatingCapacity;
    private Double fuelCapacity;
    /** km (Integration-Contracts Q9). */
    private Double maximumRange;
    private Double cruisingSpeed;

    private Long sourceVersion;
    private String ownerInstance;
    private String lastEventId;
    private Instant lastEventAt;

    ReplicatedAircraftModel(String modelName) {
        this.modelName = modelName;
    }

    boolean isNewerThan(Long version) {
        return sourceVersion != null && version != null && version < sourceVersion;
    }

    void apply(AircraftModelEventPayload p, String ownerInstance, String eventId, Instant eventAt) {
        if (p.manufacturer() != null) this.manufacturer = p.manufacturer();
        if (p.seatingCapacity() != null) this.seatingCapacity = p.seatingCapacity();
        if (p.fuelCapacity() != null) this.fuelCapacity = p.fuelCapacity();
        if (p.maximumRange() != null) this.maximumRange = p.maximumRange();
        if (p.cruisingSpeed() != null) this.cruisingSpeed = p.cruisingSpeed();
        this.sourceVersion = p.version();
        this.ownerInstance = ownerInstance;
        this.lastEventId = eventId;
        this.lastEventAt = eventAt;
    }

    public AircraftModelSummary toSummary() {
        return new AircraftModelSummary(modelName, manufacturer, orZero(seatingCapacity), orZero(fuelCapacity),
                orZero(maximumRange), orZero(cruisingSpeed));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
