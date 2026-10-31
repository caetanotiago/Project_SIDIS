package isep.psoft.aisafe.maintenance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Representação de um registo de manutenção; também é o formato trocado entre réplicas (tolerant
 * reader). Os campos de conclusão só aparecem depois da US119.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class MaintenanceRecordDTO extends RepresentationModel<MaintenanceRecordDTO> {
    private String id;
    private Long version;
    private String aircraftRegistration;
    private String aircraftModel;
    private String templateId;
    private String templateName;
    private String templateType;
    private String description;
    private LocalDate startDate;
    private Integer expectedDurationMinutes;
    private List<String> checklist;
    private String component;
    private String status;
    private LocalDate completionDate;
    private String completionNotes;
    private BigDecimal cost;
    private Integer actualDurationMinutes;
    private Double aircraftFlightHours;
}
