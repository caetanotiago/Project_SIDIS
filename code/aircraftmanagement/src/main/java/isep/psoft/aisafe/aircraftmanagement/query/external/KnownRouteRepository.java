package isep.psoft.aisafe.aircraftmanagement.query.external;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnownRouteRepository extends JpaRepository<KnownRoute, String> {

    /** US203: the same rule Flight Routes applies in {@code GET /api/routes/compatible} (bridge B6). */
    @Query("SELECT r FROM KnownRoute r WHERE r.status = 'ACTIVE' AND r.minRange <= :range "
            + "AND r.minCapacity <= :capacity ORDER BY r.originIata, r.destinationIata, r.routeId")
    List<KnownRoute> findCompatibleRoutes(@Param("range") Double range, @Param("capacity") Integer capacity);
}
