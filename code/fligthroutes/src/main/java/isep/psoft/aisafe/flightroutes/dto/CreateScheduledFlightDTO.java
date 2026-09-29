package isep.psoft.aisafe.flightroutes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
public class CreateScheduledFlightDTO {

    @NotBlank(message = "Aircraft registration is required")
    private String aircraftRegistration;

    @NotBlank(message = "Route ID is required")
    private String routeID;

    @NotNull(message = "Flight date is required")
    private LocalDate date;

    @NotNull(message = "Flight time is required")
    private LocalTime time;
}
