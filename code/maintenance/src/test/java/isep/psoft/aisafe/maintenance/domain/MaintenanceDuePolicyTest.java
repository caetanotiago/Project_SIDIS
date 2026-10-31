package isep.psoft.aisafe.maintenance.domain;

import isep.psoft.aisafe.maintenance.domain.alerts.DueContext;
import isep.psoft.aisafe.maintenance.domain.alerts.DueTrigger;
import isep.psoft.aisafe.maintenance.domain.alerts.MaintenanceAlert;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceDuePolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);
    private final MaintenanceDuePolicy policy = MaintenanceDuePolicy.standard();

    private static DueContext ctx(Integer hoursInterval, Integer daysInterval, LocalDate lastCompletion,
                                  Double hoursAtCompletion, double currentHours) {
        return new DueContext("CS-TUA", "A320", LocalDate.of(2026, 1, 1), "tpl", "A-Check",
                hoursInterval, daysInterval, lastCompletion, hoursAtCompletion, currentHours, TODAY);
    }

    @Test
    void flightHoursSinceTheLastCompletion() {
        assertTrue(policy.evaluate(ctx(500, null, TODAY.minusDays(1), 1000.0, 1499)).isEmpty());

        List<MaintenanceAlert> alerts = policy.evaluate(ctx(500, null, TODAY.minusDays(1), 1000.0, 1500));
        assertEquals(1, alerts.size());
        assertEquals(DueTrigger.FLIGHT_HOURS, alerts.get(0).trigger());
        assertEquals(500, alerts.get(0).current());
    }

    @Test
    void neverPerformedOrHoursNotRecordedCountsEveryFlightHour() {
        assertEquals(1, policy.evaluate(ctx(500, null, null, null, 600)).size());
        assertEquals(1, policy.evaluate(ctx(500, null, TODAY.minusDays(1), null, 600)).size());
        assertTrue(policy.evaluate(ctx(500, null, null, null, 100)).isEmpty());
    }

    @Test
    void calendarDaysSinceTheLastCompletionOrTheManufacturingDate() {
        assertTrue(policy.evaluate(ctx(null, 120, TODAY.minusDays(119), null, 0)).isEmpty());
        assertEquals(DueTrigger.CALENDAR_DAYS, policy.evaluate(ctx(null, 120, TODAY.minusDays(120), null, 0)).get(0).trigger());
        // nunca feito: conta desde o fabrico (2026-01-01 → 303 dias)
        assertEquals(303, policy.evaluate(ctx(null, 120, null, null, 0)).get(0).current());
    }

    @Test
    void bothCriteriaCanTriggerAndNonPeriodicTemplatesNever() {
        assertEquals(2, policy.evaluate(ctx(500, 120, TODAY.minusDays(200), 0.0, 900)).size());
        assertTrue(policy.evaluate(ctx(null, null, null, null, 99999)).isEmpty());
        assertTrue(policy.evaluate(ctx(500, 120, TODAY.minusDays(200), 0.0, 900)).get(0).message().contains("CS-TUA"));
    }
}
