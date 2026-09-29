package isep.psoft.aisafe.flightroutes.domain;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteDistance {
    
    private Double distance;

    public RouteDistance(Double distance) {
        this.distance = distance;
    }
}