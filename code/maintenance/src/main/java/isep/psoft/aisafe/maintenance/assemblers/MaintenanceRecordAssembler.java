package isep.psoft.aisafe.maintenance.assemblers;

import isep.psoft.aisafe.maintenance.controllers.MaintenanceRecordController;
import isep.psoft.aisafe.maintenance.controllers.MaintenanceTemplateController;
import isep.psoft.aisafe.maintenance.domain.CompletionDetails;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.PageDTO;
import isep.psoft.aisafe.maintenance.query.ReplicatedMaintenanceRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class MaintenanceRecordAssembler {

    private final String instanceId;

    public MaintenanceRecordAssembler(@Value("${aisafe.instance-id}") String instanceId) {
        this.instanceId = instanceId;
    }

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
        dto.setOwnerInstance(instanceId);
        return withLinks(dto);
    }

    public MaintenanceRecordDTO toDTO(ReplicatedMaintenanceRecord r) {
        MaintenanceRecordDTO dto = new MaintenanceRecordDTO();
        dto.setId(r.getRecordId());
        dto.setVersion(r.getSourceVersion());
        dto.setAircraftRegistration(r.getAircraftRegistration());
        dto.setAircraftModel(r.getAircraftModel());
        dto.setTemplateId(r.getTemplateId());
        dto.setTemplateName(r.getTemplateName());
        dto.setTemplateType(r.getTemplateType());
        dto.setDescription(r.getDescription());
        dto.setStartDate(r.getStartDate());
        dto.setExpectedDurationMinutes(r.getExpectedDurationMinutes());
        dto.setChecklist(r.getChecklist());
        dto.setComponent(r.getComponent());
        dto.setStatus(r.getStatus());
        dto.setCompletionDate(r.getCompletionDate());
        dto.setCompletionNotes(r.getCompletionNotes());
        dto.setCost(r.getCost());
        dto.setActualDurationMinutes(r.getActualDurationMinutes());
        dto.setAircraftFlightHours(r.getAircraftFlightHours());
        dto.setOwnerInstance(r.getOwnerInstance());
        return withLinks(dto);
    }

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
