package isep.psoft.aisafe.maintenance.repositories;

import isep.psoft.aisafe.maintenance.domain.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persistence operations for maintenance aggregates owned by this replica.
 *
 * @author Diogo Fernandes - 1240632
 */
public interface MaintenanceRepository extends JpaRepository<Maintenance, String> {

    List<Maintenance> findByAircraftRegistrationOrderByScheduledDateDescIdDesc(String aircraftRegistration);

    List<Maintenance> findAllByOrderByScheduledDateDescIdDesc();
}
