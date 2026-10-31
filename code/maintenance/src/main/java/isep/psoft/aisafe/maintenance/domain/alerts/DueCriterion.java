package isep.psoft.aisafe.maintenance.domain.alerts;

import java.util.Optional;

/**
 * Strategy (US222): um critério que decide se a manutenção está em atraso. Um novo critério
 * (ex.: ciclos de aterragem) é uma nova implementação, sem alterar a política (OCP).
 */
public interface DueCriterion {

    Optional<MaintenanceAlert> check(DueContext context);
}
