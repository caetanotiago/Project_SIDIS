package isep.psoft.aisafe.maintenance.events;

import isep.psoft.aisafe.maintenance.domain.MaintenanceInterval;
import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;

import java.util.List;

public record MaintenanceTemplateEventPayload(
        String templateId,
        String name,
        String type,
        List<String> applicableModels,
        List<String> checklist,
        Integer flightHoursInterval,
        Integer calendarDaysInterval,
        Long version) {

    public static MaintenanceTemplateEventPayload of(MaintenanceTemplate t) {
        MaintenanceInterval interval = t.getInterval();
        return new MaintenanceTemplateEventPayload(t.getId(), t.getName(), t.getType().name(),
                t.getApplicableModels().stream().sorted().toList(), t.getChecklist(),
                interval == null ? null : interval.getFlightHours(),
                interval == null ? null : interval.getCalendarDays(),
                t.getVersion());
    }
}
