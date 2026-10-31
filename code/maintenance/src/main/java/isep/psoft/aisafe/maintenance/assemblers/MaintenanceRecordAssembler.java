package isep.psoft.aisafe.maintenance.assemblers;

import isep.psoft.aisafe.maintenance.controllers.MaintenanceRecordController;
import isep.psoft.aisafe.maintenance.controllers.MaintenanceTemplateController;
import isep.psoft.aisafe.maintenance.domain.CompletionDetails;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.PageDTO;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/** Domain → DTO (Published Language) + links HATEOAS. */
@Component
public class MaintenanceRecordAssembler {

    public MaintenanceRecordDTO toDTO(MaintenanceRecord r) {
        MaintenanceRecordDTO dto = new MaintenanceRecordDTO();
        dto.setId(r.getId());
        dto.setVersion(r.getVersion());
        dto.setAircraftRegistration(r.getAircraftRegistration());
        dto.setAircraftModel(r.getAircraftModel());
        dto.setTemplateId(r.getTemplate().getTemplateId());
        dto.setTemplateName(r.getTemplate().getTemplateName());
        dto.setTemplateType(r.getTemplate().getTemplateType().name());
        dto.setDescription(r.getDetails().getDescription());
        dto.setStartDate(r.getDetails().getStartDate());
        dto.setExpectedDurationMinutes(r.getDetails().getExpectedDurationMinutes());
        dto.setChecklist(r.getChecklist());
        dto.setComponent(r.getComponent() == null ? null : r.getComponent().name());
        dto.setStatus(r.getStatus().name());
        CompletionDetails c = r.getCompletion();
        if (c != null) {
            dto.setCompletionDate(c.getCompletionDate());
            dto.setCompletionNotes(c.getNotes());
            dto.setCost(c.getCost());
            dto.setActualDurationMinutes(c.getActualDurationMinutes());
            dto.setAircraftFlightHours(c.getAircraftFlightHours());
        }
        return withLinks(dto);
    }

    /**
     * (Re)cria os links a apontar para ESTA réplica. Usado também nos registos recebidos de peers:
     * os links continuam válidos aqui, porque esta réplica encaminha o pedido ao dono.
     */
    public MaintenanceRecordDTO withLinks(MaintenanceRecordDTO dto) {
        dto.removeLinks();
        dto.add(linkTo(methodOn(MaintenanceRecordController.class).getById(dto.getId())).withSelfRel());
        dto.add(linkTo(methodOn(MaintenanceRecordController.class)
                .byAircraft(dto.getAircraftRegistration(), 0, PageDTO.DEFAULT_SIZE)).withRel("aircraft-records"));
        if (dto.getTemplateId() != null) {
            dto.add(linkTo(methodOn(MaintenanceTemplateController.class).getById(dto.getTemplateId()))
                    .withRel("template"));
        }
        if (MaintenanceStatus.IN_PROGRESS.name().equals(dto.getStatus())) {
            dto.add(linkTo(methodOn(MaintenanceRecordController.class).complete(dto.getId(), null))
                    .withRel("complete"));
            dto.add(linkTo(methodOn(MaintenanceRecordController.class).categorize(dto.getId(), null))
                    .withRel("categorize"));
        }
        return dto;
    }
}
