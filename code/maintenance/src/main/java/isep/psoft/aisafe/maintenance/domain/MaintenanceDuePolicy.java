package isep.psoft.aisafe.maintenance.domain;

import isep.psoft.aisafe.maintenance.domain.alerts.CalendarDaysCriterion;
import isep.psoft.aisafe.maintenance.domain.alerts.DueContext;
import isep.psoft.aisafe.maintenance.domain.alerts.DueCriterion;
import isep.psoft.aisafe.maintenance.domain.alerts.FlightHoursCriterion;
import isep.psoft.aisafe.maintenance.domain.alerts.MaintenanceAlert;

import java.util.List;
import java.util.Optional;

public class MaintenanceDuePolicy {

    private final List<DueCriterion> criteria;

    public MaintenanceDuePolicy(List<DueCriterion> criteria) {
        this.criteria = List.copyOf(criteria);
    }

    public static MaintenanceDuePolicy standard() {
        return new MaintenanceDuePolicy(List.of(new FlightHoursCriterion(), new CalendarDaysCriterion()));
    }

    public List<MaintenanceAlert> evaluate(DueContext context) {
        return criteria.stream()
                .map(criterion -> criterion.check(context))
                .flatMap(Optional::stream)
                .toList();
    }
}
