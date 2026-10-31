package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.clients.AircraftClient.FlightHours;
import isep.psoft.aisafe.maintenance.clients.AircraftClient.Fleet;
import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.psoft.aisafe.maintenance.domain.MaintenanceDuePolicy;
import isep.psoft.aisafe.maintenance.domain.alerts.DueContext;
import isep.psoft.aisafe.maintenance.dto.LastCompletionDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceAlertDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceTemplateDTO;
import isep.sidis.common.remote.RemoteServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * US222: aeronaves em atraso para manutenção programada (horas de voo ou dias de calendário).
 * Verificação disparada manualmente pelo utilizador (conversa 016) e calculada a pedido com os dados
 * de todas as réplicas e do Aircraft Management; os alertas não são guardados.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceAlertService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceAlertService.class);

    private final AircraftClient aircraftClient;
    private final ViewMaintenanceTemplatesService templates;
    private final MaintenanceStatisticsService statistics;
    private final MaintenanceDuePolicy policy = MaintenanceDuePolicy.standard();

    /** Alertas + se algum dos dados usados pode estar incompleto. */
    public record AlertResult(List<MaintenanceAlertDTO> alerts, boolean partial) { }

    public AlertResult checkFleet() {
        Fleet fleet = aircraftClient.fleet();
        FlightHours hours = flightHoursOrNone();
        List<MaintenanceTemplateDTO> periodic = templates.list(null, null, null).stream()
                .filter(MaintenanceTemplateDTO::isPeriodic)
                .toList();
        Map<String, LastCompletionDTO> last = statistics.lastCompletions().stream()
                .collect(Collectors.toMap(LastCompletionDTO::key, Function.identity(), LastCompletionDTO::merge));
        LocalDate today = LocalDate.now();

        List<MaintenanceAlertDTO> alerts = new ArrayList<>();
        for (AircraftSnapshot aircraft : fleet.aircraft()) {
            if (aircraft.isInactive()) {
                continue;
            }
            for (MaintenanceTemplateDTO template : periodic) {
                if (!template.appliesTo(aircraft.modelName())) {
                    continue;
                }
                LastCompletionDTO previous = last.get(LastCompletionDTO.key(aircraft.registrationNumber(), template.getId()));
                if (previous != null && previous.ongoing()) {
                    continue; // já está a ser feita
                }
                DueContext context = new DueContext(aircraft.registrationNumber(), aircraft.modelName(),
                        aircraft.manufacturingDate(), template.getId(), template.getName(),
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

    /**
     * Degradação graciosa: as horas de voo vêm do Flight Routes através do Aircraft Management (US206).
     * Se não estiverem disponíveis, os alertas por dias de calendário continuam a ser calculados e a
     * resposta é marcada como parcial, em vez de falhar toda a verificação.
     */
    private FlightHours flightHoursOrNone() {
        try {
            return aircraftClient.flightHours();
        } catch (RemoteServiceUnavailableException | ResponseStatusException e) {
            log.warn("Flight hours unavailable ({}): only calendar-day alerts are computed", e.getMessage());
            return new FlightHours(Map.of(), true);
        }
    }
}
