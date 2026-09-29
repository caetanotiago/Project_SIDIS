package isep.psoft.aisafe.flightroutes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Exigido pelo JPA
public class FlightSchedule {

    @Column(name = "flight_date")
    private LocalDate date;

    @Column(name = "flight_time")
    private LocalTime time;

    public FlightSchedule(LocalDate date, LocalTime time) {
        if (date == null) {
            throw new IllegalArgumentException("Flight date is required.");
        }
        if (time == null) {
            throw new IllegalArgumentException("Flight time is required.");
        }
        this.date = date;
        this.time = time;
    }
}
