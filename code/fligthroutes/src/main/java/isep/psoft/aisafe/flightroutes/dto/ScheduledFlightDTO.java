package isep.psoft.aisafe.flightroutes.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class ScheduledFlightDTO extends RepresentationModel<ScheduledFlightDTO> {
    private String id;
    private String aircraftRegistration;
    private String routeID;
    private String originIATA;
    private String destIATA;
    private LocalDate date;
    private LocalTime time;
    private String status;
}
