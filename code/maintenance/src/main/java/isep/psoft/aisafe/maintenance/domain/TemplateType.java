package isep.psoft.aisafe.maintenance.domain;

public enum TemplateType {
    INSPECTION,
    SCHEDULED_MAINTENANCE,
    OVERHAUL,
    MODIFICATION;

    public static TemplateType parse(String value) {
        return Enums.parse(TemplateType.class, value, "template type");
    }
}
