package isep.psoft.aisafe.maintenance.query;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReplicatedMaintenanceRecordRepository extends JpaRepository<ReplicatedMaintenanceRecord, String> {

    List<ReplicatedMaintenanceRecord> findByStatus(String status);

    List<ReplicatedMaintenanceRecord> findByAircraftRegistrationIgnoreCaseAndStatus(String aircraftRegistration,
                                                                                    String status);
}
