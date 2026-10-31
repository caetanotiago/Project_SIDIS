package isep.psoft.aisafe.maintenance.domain;

import java.util.function.Function;

/**
 * Strategy (US220): critério de agrupamento do relatório de custos. Um novo critério é uma nova
 * constante, sem alterar o serviço (Protected Variation).
 */
public enum CostGrouping {
    AIRCRAFT(MaintenanceRecord::getAircraftRegistration),
    MODEL(MaintenanceRecord::getAircraftModel);

    private final Function<MaintenanceRecord, String> key;

    CostGrouping(Function<MaintenanceRecord, String> key) {
        this.key = key;
    }

    public String keyOf(MaintenanceRecord maintenanceRecord) {
        return key.apply(maintenanceRecord);
    }

    public static CostGrouping parse(String value) {
        return value == null || value.isBlank() ? AIRCRAFT : Enums.parse(CostGrouping.class, value, "cost grouping");
    }
}
