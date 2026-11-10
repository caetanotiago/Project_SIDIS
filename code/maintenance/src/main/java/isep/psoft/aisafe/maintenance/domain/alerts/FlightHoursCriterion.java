package isep.psoft.aisafe.maintenance.domain.alerts;

import java.util.Optional;

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
