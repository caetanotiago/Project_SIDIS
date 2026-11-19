package isep.psoft.aisafe.flightroutes.query.external;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnownAirportRepository extends JpaRepository<KnownAirport, String> {
}
