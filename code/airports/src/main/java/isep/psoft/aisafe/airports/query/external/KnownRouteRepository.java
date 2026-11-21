package isep.psoft.aisafe.airports.query.external;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso ao read model {@code KNOWN_ROUTE} desta réplica. */
public interface KnownRouteRepository extends JpaRepository<KnownRoute, String> {
}
