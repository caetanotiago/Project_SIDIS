package isep.psoft.aisafe.maintenance.query;

import isep.psoft.aisafe.maintenance.clients.AircraftClient.Fleet;
import isep.psoft.aisafe.maintenance.clients.AircraftClient.FlightHours;
import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.psoft.aisafe.maintenance.domain.MaintenanceDuePolicy;
import isep.psoft.aisafe.maintenance.domain.alerts.DueContext;
import isep.psoft.aisafe.maintenance.dto.LastCompletionDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceAlertDTO;
import isep.psoft.aisafe.maintenance.query.external.FleetDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MaintenanceAlertService {

    private final FleetDirectory fleetDirectory;
    private final ViewMaintenanceTemplatesService templates;
    private final MaintenanceStatisticsService statistics;
    private final MaintenanceDuePolicy policy = MaintenanceDuePolicy.standard();

    public record AlertResult(List<MaintenanceAlertDTO> alerts, boolean partial) { }

    public AlertResult checkFleet() {
        Fleet fleet = fleetDirectory.fleet();
        FlightHours hours = fleetDirectory.flightHours();
        List<ReplicatedMaintenanceTemplate> periodic = templates.periodic();
        Map<String, LastCompletionDTO> last = statistics.lastCompletions();
        LocalDate today = LocalDate.now();

        List<MaintenanceAlertDTO> alerts = new ArrayList<>();
        for (AircraftSnapshot aircraft : fleet.aircraft()) {
            if (aircraft.isInactive()) {
                continue;
            }
            for (ReplicatedMaintenanceTemplate template : periodic) {
                if (!template.appliesTo(aircraft.modelName())) {
                    continue;
                }
                LastCompletionDTO previous = last.get(LastCompletionDTO.key(aircraft.registrationNumber(),
                        template.getTemplateId()));
                if (previous != null && previous.ongoing()) {
                    continue;
                }
                DueContext context = new DueContext(aircraft.registrationNumber(), aircraft.modelName(),
                        aircraft.manufacturingDate(), template.getTemplateId(), template.getName(),
                        template.getFlightHoursInterval(), template.getCalendarDaysInterval(),
                        previous == null ? null : previous.lastCompletionDate(),
                        previous == null ? null : previous.aircraftFlightHours(),
                        hours.of(aircraft.registrationNumber()), today);
                policy.evaluate(context).forEach(a -> alerts.add(MaintenanceAlertDTO.from(a)));
            }
        }
        alerts.sort(Comparator.comparing(MaintenanceAlertDTO::aircraftRegistration)
                .thenComparing(MaintenanceAlertDTO::templateName)
                .thenComparing(MaintenanceAlertDTO::trigger));
        return new AlertResult(alerts, fleet.partial() || hours.partial());
    }
}
