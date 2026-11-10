package isep.psoft.aisafe.maintenance.domain;

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
public class MaintenanceInterval {

    @Column(name = "interval_flight_hours")
    private Integer flightHours;

    @Column(name = "interval_calendar_days")
    private Integer calendarDays;

    public MaintenanceInterval(Integer flightHours, Integer calendarDays) {
        if (flightHours == null && calendarDays == null) {
            throw new IllegalArgumentException("A maintenance interval needs flight hours and/or calendar days.");
        }
        if (flightHours != null && flightHours <= 0) {
            throw new IllegalArgumentException("The flight hours interval must be positive.");
        }
        if (calendarDays != null && calendarDays <= 0) {
            throw new IllegalArgumentException("The calendar days interval must be positive.");
        }
        this.flightHours = flightHours;
        this.calendarDays = calendarDays;
    }

    public static MaintenanceInterval ofOptional(Integer flightHours, Integer calendarDays) {
        return flightHours == null && calendarDays == null ? null : new MaintenanceInterval(flightHours, calendarDays);
    }
}
