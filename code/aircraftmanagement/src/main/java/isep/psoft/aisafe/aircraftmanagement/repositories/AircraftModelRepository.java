package isep.psoft.aisafe.aircraftmanagement.repositories;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftModelRepository extends JpaRepository<AircraftModel, String> {
}
