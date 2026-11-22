package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.domain.MaintenanceComponent;
import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;

import java.time.LocalDate;

public record RecordFilter(String aircraft, MaintenanceComponent component, MaintenanceStatus status,
                           LocalDate from, LocalDate to) {

    public RecordFilter {
        aircraft = aircraft == null || aircraft.isBlank() ? null : aircraft.trim().toUpperCase();
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' (" + from + ") cannot be after 'to' (" + to + ").");
        }
    }

    public static RecordFilter of(String aircraft, String component, String status, LocalDate from, LocalDate to) {
        return new RecordFilter(aircraft, MaintenanceComponent.parseOptional(component),
                status == null || status.isBlank() ? null : MaintenanceStatus.parse(status), from, to);
    }

    public static RecordFilter byAircraft(String registration) {
        return new RecordFilter(registration, null, null, null, null);
    }

    public static RecordFilter ongoing() {
        return new RecordFilter(null, null, MaintenanceStatus.IN_PROGRESS, null, null);
    }

    public boolean matches(ReplicatedMaintenanceRecord r) {
        LocalDate start = r.getStartDate();
        return (aircraft == null || aircraft.equalsIgnoreCase(r.getAircraftRegistration()))
                && (component == null || component.name().equals(r.getComponent()))
                && (status == null || status.name().equals(r.getStatus()))
                && (from == null || (start != null && !start.isBefore(from)))
                && (to == null || (start != null && !start.isAfter(to)));
    }
}
