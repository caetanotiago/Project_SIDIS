package isep.psoft.aisafe.maintenance.query;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplicatedMaintenanceTemplateRepository extends JpaRepository<ReplicatedMaintenanceTemplate, String> {

    boolean existsByNameIgnoreCase(String name);
}
