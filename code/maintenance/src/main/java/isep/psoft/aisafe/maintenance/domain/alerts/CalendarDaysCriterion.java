package isep.psoft.aisafe.maintenance.domain.alerts;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Dias de calendário desde a última conclusão; se o template nunca foi feito na aeronave, desde a
 * data de fabrico.
 */
public class CalendarDaysCriterion implements DueCriterion {

    @Override
    public Optional<MaintenanceAlert> check(DueContext c) {
        if (c.calendarDaysInterval() == null) {
            return Optional.empty();
        }
        LocalDate base = c.lastCompletionDate() != null ? c.lastCompletionDate() : c.manufacturingDate();
        if (base == null) {
            return Optional.empty();
        }
        long days = ChronoUnit.DAYS.between(base, c.today());
        if (days < c.calendarDaysInterval()) {
            return Optional.empty();
        }
        return Optional.of(new MaintenanceAlert(c.aircraftRegistration(), c.aircraftModel(), c.templateId(),
                c.templateName(), DueTrigger.CALENDAR_DAYS, c.calendarDaysInterval(), days, c.lastCompletionDate()));
    }
}
