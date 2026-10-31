package isep.psoft.aisafe.maintenance.domain.alerts;

import java.util.Optional;

/**
 * Horas de voo desde a última conclusão = horas atuais − horas registadas nessa conclusão.
 * Se o template nunca foi feito na aeronave, ou as horas não foram registadas, contam todas as
 * horas atuais (critério conservador: alertar mais cedo em vez de mais tarde).
 */
public class FlightHoursCriterion implements DueCriterion {

    @Override
    public Optional<MaintenanceAlert> check(DueContext c) {
        if (c.flightHoursInterval() == null) {
            return Optional.empty();
        }
        double since = c.lastCompletionDate() != null && c.flightHoursAtCompletion() != null
                ? c.currentFlightHours() - c.flightHoursAtCompletion()
                : c.currentFlightHours();
        if (since < c.flightHoursInterval()) {
            return Optional.empty();
        }
        return Optional.of(new MaintenanceAlert(c.aircraftRegistration(), c.aircraftModel(), c.templateId(),
                c.templateName(), DueTrigger.FLIGHT_HOURS, c.flightHoursInterval(),
                Math.round(since * 100.0) / 100.0, c.lastCompletionDate()));
    }
}
