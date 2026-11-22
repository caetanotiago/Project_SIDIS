package isep.psoft.aisafe.aircraftmanagement.query;

import org.springframework.data.jpa.repository.JpaRepository;

/** Read model of the aircraft models of ALL replicas, stored in THIS replica (see {@link ReplicatedAircraftModel}). */
public interface ReplicatedAircraftModelRepository extends JpaRepository<ReplicatedAircraftModel, String> {
}
