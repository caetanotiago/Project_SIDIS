package isep.psoft.aisafe.flightroutes.domain;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteDistance {

    private Double distance; // km (Haversine)

    public RouteDistance(Double distance) {
        if (distance == null || distance.isNaN() || distance < 0) {
            throw new IllegalArgumentException("Route distance must be zero or positive.");
        }
        this.distance = distance;
    }
}
