package isep.psoft.aisafe.aircraftmanagement.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * READ MODEL behind {@code GET /api/v2/unicorns} (table {@code AIRCRAFT_MOOD}): one row per aircraft with its
 * operational status and the "mood" derived from it. It is a projection of {@code AircraftRegistered} and
 * {@code AircraftStatusChanged}, kept by {@link AircraftProjection} in the same transaction as
 * {@link ReplicatedAircraft}; no client ever writes it and the command side never reads it.
 * Contract and CQRS explanation: docs/2-containers/Unicorns-Mock.md.
 */
@Entity
@Table(name = "AIRCRAFT_MOOD")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AircraftMood {

    public static final String HAPPY = "HAPPY";
    public static final String BUSY = "BUSY";
    public static final String GROUNDED_AND_GRUMPY = "GROUNDED_AND_GRUMPY";
    public static final String NEUTRAL = "NEUTRAL";

    @Id
    private String registration;

    private String model;
    private String operationalStatus;
    private String mood;

    /** When this read model last applied a status event for the aircraft (not when the status changed). */
    private Instant updatedAt;

    AircraftMood(String registration) {
        this.registration = registration;
    }

    void apply(String model, String operationalStatus, Instant appliedAt) {
        if (model != null) this.model = model;
        if (operationalStatus != null) {
            this.operationalStatus = operationalStatus;
            this.mood = moodOf(operationalStatus);
        }
        this.updatedAt = appliedAt;
    }

    /** The mood rule of the contract: a pure function of the operational status. */
    public static String moodOf(String operationalStatus) {
        if (operationalStatus == null) {
            return NEUTRAL;
        }
        return switch (operationalStatus) {
            case "AVAILABLE" -> HAPPY;
            case "IN_FLIGHT" -> BUSY;
            case "UNDER_MAINTENANCE" -> GROUNDED_AND_GRUMPY;
            default -> NEUTRAL;
        };
    }
}
