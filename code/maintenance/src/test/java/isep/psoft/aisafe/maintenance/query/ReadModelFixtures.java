package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.events.MaintenanceRecordEventPayload;
import isep.psoft.aisafe.maintenance.events.MaintenanceTemplateEventPayload;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReadModelFixtures {

    private ReadModelFixtures() {
    }

    public static ReplicatedMaintenanceTemplate template(String id, String name, Integer flightHours, Integer days,
                                                         String... models) {
        ReplicatedMaintenanceTemplate t = new ReplicatedMaintenanceTemplate(id);
        t.apply(new MaintenanceTemplateEventPayload(id, name, "INSPECTION", List.of(models),
                List.of("Inspect tyres", "Check oil"), flightHours, days, 0L), "maintenance-1",
                UUID.randomUUID().toString(), Instant.now());
        return t;
    }

    public static ReplicatedMaintenanceRecord ongoing(String id, String registration, String model, String templateId,
                                                      int expectedMinutes) {
        ReplicatedMaintenanceRecord r = new ReplicatedMaintenanceRecord(id);
        r.apply(new MaintenanceRecordEventPayload(id, registration, model, templateId, "A-Check", "INSPECTION", "d",
                LocalDate.now(), expectedMinutes, List.of("x"), null, "IN_PROGRESS", null, null, null, null, null, 0L),
                "maintenance-1", UUID.randomUUID().toString(), Instant.now());
        return r;
    }

    public static ReplicatedMaintenanceRecord completed(String id, String registration, String model, String templateId,
                                                        String cost, int actualMinutes, LocalDate date, Double hours) {
        ReplicatedMaintenanceRecord r = new ReplicatedMaintenanceRecord(id);
        r.apply(new MaintenanceRecordEventPayload(id, registration, model, templateId, "A-Check", "INSPECTION", "d",
                date, 100, List.of("x"), null, "COMPLETED", date, "ok", new BigDecimal(cost), actualMinutes, hours, 1L),
                "maintenance-2", UUID.randomUUID().toString(), Instant.now());
        return r;
    }
}
