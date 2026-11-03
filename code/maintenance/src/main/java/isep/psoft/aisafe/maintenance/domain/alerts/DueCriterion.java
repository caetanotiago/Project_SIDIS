package isep.psoft.aisafe.maintenance.domain.alerts;

import java.util.Optional;

public interface DueCriterion {

    Optional<MaintenanceAlert> check(DueContext context);
}
