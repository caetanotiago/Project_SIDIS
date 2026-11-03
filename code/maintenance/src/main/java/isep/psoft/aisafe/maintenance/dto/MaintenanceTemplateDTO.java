package isep.psoft.aisafe.maintenance.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class MaintenanceTemplateDTO extends RepresentationModel<MaintenanceTemplateDTO> {
    private String id;
    private String name;
    private String type;
    private List<String> applicableModels;
    private List<String> checklist;
    private Integer flightHoursInterval;
    private Integer calendarDaysInterval;

    public boolean appliesTo(String modelName) {
        return modelName != null && applicableModels != null
                && applicableModels.stream().anyMatch(m -> m.equalsIgnoreCase(modelName.trim()));
    }

    @JsonIgnore
    public boolean isPeriodic() {
        return flightHoursInterval != null || calendarDaysInterval != null;
    }
}
