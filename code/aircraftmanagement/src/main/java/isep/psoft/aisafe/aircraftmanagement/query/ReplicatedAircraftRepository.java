package isep.psoft.aisafe.aircraftmanagement.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Read model of the aircraft of ALL replicas, stored in THIS replica (see {@link ReplicatedAircraft}). Every aircraft
 * query of the REST API is answered from here; nothing is aggregated with the peers.
 */
public interface ReplicatedAircraftRepository extends JpaRepository<ReplicatedAircraft, String> {

    // US104 (filters are applied in memory, the fleet is small), US204, US205, US206
    @Query("SELECT a FROM ReplicatedAircraft a ORDER BY a.registrationNumber")
    List<ReplicatedAircraft> findAllOrdered();
}
