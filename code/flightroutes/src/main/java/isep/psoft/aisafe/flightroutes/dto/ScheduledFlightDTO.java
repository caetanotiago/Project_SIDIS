package isep.psoft.aisafe.flightroutes.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDate;
import java.time.LocalTime;

// Tolera campos extra (ex.: _links) nas respostas de outras réplicas
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class ScheduledFlightDTO extends RepresentationModel<ScheduledFlightDTO> {
    private String id;
    private String aircraftRegistration;
    private String routeID;
    private String originIATA;
    private String destIATA;
    private Integer estimatedFlightTime; // minutos (cópia da rota no momento do agendamento)
    private LocalDate date;
    private LocalTime time;
    private String status;
}
