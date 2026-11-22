package isep.psoft.aisafe.airports.query;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicatedRunway {
    private String name;
    private Integer lengthMeters;
    private Integer orientation;
    private String surface;

    ReplicatedRunway(String name, Integer lengthMeters, Integer orientation, String surface) {
        this.name = name;
        this.lengthMeters = lengthMeters;
        this.orientation = orientation;
        this.surface = surface;
    }
}
