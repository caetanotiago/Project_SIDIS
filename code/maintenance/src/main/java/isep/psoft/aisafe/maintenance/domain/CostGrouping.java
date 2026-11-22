package isep.psoft.aisafe.maintenance.domain;

import java.util.function.BinaryOperator;

public enum CostGrouping {
    AIRCRAFT((registration, model) -> registration),
    MODEL((registration, model) -> model);

    private final BinaryOperator<String> key;

    CostGrouping(BinaryOperator<String> key) {
        this.key = key;
    }

    public String keyOf(String aircraftRegistration, String aircraftModel) {
        return key.apply(aircraftRegistration, aircraftModel);
    }

    public String keyOf(MaintenanceRecord maintenanceRecord) {
        return keyOf(maintenanceRecord.getAircraftRegistration(), maintenanceRecord.getAircraftModel());
    }

    public static CostGrouping parse(String value) {
        return value == null || value.isBlank() ? AIRCRAFT : Enums.parse(CostGrouping.class, value, "cost grouping");
    }
}
