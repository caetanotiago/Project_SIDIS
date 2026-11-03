package isep.psoft.aisafe.maintenance.domain;

public enum MaintenanceStatus {
    IN_PROGRESS,
    COMPLETED;

    public static MaintenanceStatus parse(String value) {
        return Enums.parse(MaintenanceStatus.class, value, "maintenance status");
    }
}
