package isep.psoft.aisafe.airports.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Runway {

    @Column(name = "runway_name", nullable = false)
    private String name;

    @Column(name = "length_meters", nullable = false)
    private int lengthMeters;

    @Column(nullable = false)
    private int orientation;

    private String surface;

    public Runway(String name, Integer lengthMeters, Integer orientation, String surface) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Runway name is required.");
        }
        if (lengthMeters == null || lengthMeters <= 0) {
            throw new IllegalArgumentException("Runway length must be positive.");
        }
        if (orientation == null || orientation < 0 || orientation > 359) {
            throw new IllegalArgumentException("Runway orientation must be between 0 and 359 degrees.");
        }
        this.name = name.trim().toUpperCase();
        this.lengthMeters = lengthMeters;
        this.orientation = orientation;
        this.surface = surface == null || surface.isBlank() ? null : surface.trim().toUpperCase();
    }
}
