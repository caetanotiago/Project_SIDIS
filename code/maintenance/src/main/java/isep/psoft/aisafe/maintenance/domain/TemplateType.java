package isep.psoft.aisafe.maintenance.domain;

/** Tipo de template (US115): inspeção, manutenção programada, revisão geral ou modificação. */
public enum TemplateType {
    INSPECTION,
    SCHEDULED_MAINTENANCE,
    OVERHAUL,
    MODIFICATION;

    public static TemplateType parse(String value) {
        return Enums.parse(TemplateType.class, value, "template type");
    }
}
