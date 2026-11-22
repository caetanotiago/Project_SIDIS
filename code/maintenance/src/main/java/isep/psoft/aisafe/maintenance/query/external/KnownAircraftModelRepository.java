package isep.psoft.aisafe.maintenance.query.external;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnownAircraftModelRepository extends JpaRepository<KnownAircraftModel, String> {

    boolean existsByModelNameIgnoreCase(String modelName);
}
