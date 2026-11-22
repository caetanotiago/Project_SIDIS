package isep.psoft.aisafe.airports.query.external;

import org.springframework.data.jpa.repository.JpaRepository;

// Acesso a KNOWN_ROUTE
public interface KnownRouteRepository extends JpaRepository<KnownRoute, String> {
}
