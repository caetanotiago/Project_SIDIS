package isep.psoft.aisafe.aircraftmanagement.repositories;

import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftRepository extends JpaRepository<Aircraft, String> {
}
