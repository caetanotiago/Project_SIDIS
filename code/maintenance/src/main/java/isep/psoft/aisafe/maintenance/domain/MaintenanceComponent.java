package isep.psoft.aisafe.maintenance.domain;

/** Componente da aeronave a que um registo de manutenção diz respeito (US217). */
public enum MaintenanceComponent {
    ENGINE,
    AIRFRAME,
    AVIONICS,
    INTERIOR,
    EXTERIOR;

    public static MaintenanceComponent parse(String value) {
        return Enums.parse(MaintenanceComponent.class, value, "maintenance component");
    }

    /** Componente opcional: null/vazio → null. */
    public static MaintenanceComponent parseOptional(String value) {
        return value == null || value.isBlank() ? null : parse(value);
    }
}
