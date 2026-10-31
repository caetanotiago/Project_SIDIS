package isep.psoft.aisafe.maintenance.repositories;

import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

/** Templates guardados NESTA réplica. */
public interface MaintenanceTemplateRepository extends JpaRepository<MaintenanceTemplate, String> {

    boolean existsByNameIgnoreCase(String name);
}
