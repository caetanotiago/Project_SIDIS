package isep.psoft.aisafe.maintenance.domain;

/**
 * Estado de um registo de manutenção. Um registo é criado IN_PROGRESS (US115) e passa uma única vez
 * a COMPLETED (US119). "Ongoing" (US219) = IN_PROGRESS.
 */
public enum MaintenanceStatus {
    IN_PROGRESS,
    COMPLETED;

    public static MaintenanceStatus parse(String value) {
        return Enums.parse(MaintenanceStatus.class, value, "maintenance status");
    }
}
