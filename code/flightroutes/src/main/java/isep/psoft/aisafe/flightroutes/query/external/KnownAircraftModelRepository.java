package isep.psoft.aisafe.flightroutes.query.external;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnownAircraftModelRepository extends JpaRepository<KnownAircraftModel, String> {
}
