package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.domain.MaintenanceComponent;
import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;

import java.time.LocalDate;

/**
 * Value Object com os filtros de pesquisa de registos (US116, US218, US219). Filtros nulos não
 * restringem; os restantes combinam-se com AND. O mesmo filtro é aplicado aos dados locais e é
 * enviado às réplicas peer como query string ({@link #toUri()}).
 *
 * @param from/to intervalo (inclusivo) da data de início
 */
public record RecordFilter(String aircraft, MaintenanceComponent component, MaintenanceStatus status,
                           LocalDate from, LocalDate to) {

    public static final String SEARCH_PATH = "/api/maintenance-records/search";

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

    public static RecordFilter ongoingOf(String registration) {
        return new RecordFilter(registration, null, MaintenanceStatus.IN_PROGRESS, null, null);
    }

    public boolean matches(MaintenanceRecord r) {
        LocalDate start = r.getDetails().getStartDate();
        return (aircraft == null || aircraft.equalsIgnoreCase(r.getAircraftRegistration()))
                && (component == null || component == r.getComponent())
                && (status == null || status == r.getStatus())
                && (from == null || !start.isBefore(from))
                && (to == null || !start.isAfter(to));
    }

    /** URI (codificada) do endpoint de pesquisa não paginada, para enviar às réplicas peer. */
    public String toUri() {
        return MaintenanceReplicaQueries.uri(SEARCH_PATH,
                "aircraft", aircraft, "component", component, "status", status, "from", from, "to", to);
    }
}
