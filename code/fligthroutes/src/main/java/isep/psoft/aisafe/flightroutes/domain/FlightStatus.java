package isep.psoft.aisafe.flightroutes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlightStatus {

    // Lifecycle states approved by the client (Forum conv. 006):
    // SCHEDULED, DELAYED, IN_FLIGHT, COMPLETED, CANCELED.
    // State transitions are future functionality; a flight is born SCHEDULED.
    @Column(name = "flight_status")
    private String state;

    public FlightStatus(String state) {
        if (state == null || state.trim().isEmpty()) {
            throw new IllegalArgumentException("Flight status cannot be empty.");
        }
        this.state = state.trim().toUpperCase();
    }

    public static FlightStatus scheduled() {
        return new FlightStatus("SCHEDULED");
    }
}
