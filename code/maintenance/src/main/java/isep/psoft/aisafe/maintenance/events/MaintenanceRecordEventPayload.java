package isep.psoft.aisafe.maintenance.events;

import isep.psoft.aisafe.maintenance.domain.CompletionDetails;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MaintenanceRecordEventPayload(
        String recordId,
        String aircraftRegistration,
        String aircraftModel,
        String templateId,
        String templateName,
        String templateType,
        String description,
        LocalDate startDate,
        Integer expectedDurationMinutes,
        List<String> checklist,
        String component,
        String status,
        LocalDate completionDate,
        String completionNotes,
        BigDecimal cost,
        Integer actualDurationMinutes,
        Double aircraftFlightHours,
        Long version) {

    public static MaintenanceRecordEventPayload of(MaintenanceRecord r) {
        CompletionDetails c = r.getCompletion();
        return new MaintenanceRecordEventPayload(
                r.getId(),
                r.getAircraftRegistration(),
                r.getAircraftModel(),
                r.getTemplate().getTemplateId(),
                r.getTemplate().getTemplateName(),
                r.getTemplate().getTemplateType().name(),
                r.getDetails().getDescription(),
                r.getDetails().getStartDate(),
                r.getDetails().getExpectedDurationMinutes(),
                r.getChecklist(),
                r.getComponent() == null ? null : r.getComponent().name(),
                r.getStatus().name(),
                c == null ? null : c.getCompletionDate(),
                c == null ? null : c.getNotes(),
                c == null ? null : c.getCost(),
                c == null ? null : c.getActualDurationMinutes(),
                c == null ? null : c.getAircraftFlightHours(),
                r.getVersion());
    }
}
