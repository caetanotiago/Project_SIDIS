package isep.psoft.aisafe.maintenance.repositories;

import isep.psoft.aisafe.maintenance.domain.MaintenanceRecord;
import isep.psoft.aisafe.maintenance.domain.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Registos guardados NESTA réplica (os das outras réplicas obtêm-se pelo PeerClient). */
public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, String> {

    List<MaintenanceRecord> findByStatus(MaintenanceStatus status);

    List<MaintenanceRecord> findByAircraftRegistrationIgnoreCase(String aircraftRegistration);
}
