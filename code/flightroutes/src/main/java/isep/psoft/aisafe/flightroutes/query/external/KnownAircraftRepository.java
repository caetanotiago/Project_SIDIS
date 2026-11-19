package isep.psoft.aisafe.flightroutes.query.external;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnownAircraftRepository extends JpaRepository<KnownAircraft, String> {
}
