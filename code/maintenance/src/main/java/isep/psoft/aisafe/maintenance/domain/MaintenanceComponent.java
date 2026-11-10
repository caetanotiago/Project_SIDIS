package isep.psoft.aisafe.maintenance.domain;

public enum MaintenanceComponent {
    ENGINE,
    AIRFRAME,
    AVIONICS,
    INTERIOR,
    EXTERIOR;

    public static MaintenanceComponent parse(String value) {
        return Enums.parse(MaintenanceComponent.class, value, "maintenance component");
    }

    public static MaintenanceComponent parseOptional(String value) {
        return value == null || value.isBlank() ? null : parse(value);
    }
}
