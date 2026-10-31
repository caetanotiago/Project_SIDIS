package isep.psoft.aisafe.maintenance.assemblers;

import isep.psoft.aisafe.maintenance.controllers.MaintenanceTemplateController;
import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;
import isep.psoft.aisafe.maintenance.dto.MaintenanceTemplateDTO;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/** Domain → DTO + links HATEOAS (também para templates vindos de peers). */
@Component
public class MaintenanceTemplateAssembler {

    public MaintenanceTemplateDTO toDTO(MaintenanceTemplate t) {
        MaintenanceTemplateDTO dto = new MaintenanceTemplateDTO();
        dto.setId(t.getId());
        dto.setName(t.getName());
        dto.setType(t.getType().name());
        dto.setApplicableModels(t.getApplicableModels().stream().sorted().toList());
        dto.setChecklist(t.getChecklist());
        if (t.getInterval() != null) {
            dto.setFlightHoursInterval(t.getInterval().getFlightHours());
            dto.setCalendarDaysInterval(t.getInterval().getCalendarDays());
        }
        return withLinks(dto);
    }

    public MaintenanceTemplateDTO withLinks(MaintenanceTemplateDTO dto) {
        dto.removeLinks();
        dto.add(linkTo(methodOn(MaintenanceTemplateController.class).getById(dto.getId())).withSelfRel());
        dto.add(linkTo(methodOn(MaintenanceTemplateController.class).list(null, null, null)).withRel("templates"));
        return dto;
    }

    public List<MaintenanceTemplateDTO> toDTOList(List<MaintenanceTemplate> templates) {
        return templates.stream().map(this::toDTO).toList();
    }
}
