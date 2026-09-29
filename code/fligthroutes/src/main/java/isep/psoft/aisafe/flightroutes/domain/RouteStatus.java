package isep.psoft.aisafe.flightroutes.domain;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteStatus {

    private String state; // Ex: "ACTIVE", "INACTIVE"

    public RouteStatus(String state) {
        this.state = state;
    }

    public static RouteStatus active() {
        return new RouteStatus("ACTIVE");
    }

    public static RouteStatus inactive() {
        return new RouteStatus("INACTIVE");
    }
}