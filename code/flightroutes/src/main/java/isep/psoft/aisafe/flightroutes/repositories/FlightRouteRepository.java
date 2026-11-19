package isep.psoft.aisafe.flightroutes.repositories;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * WRITE MODEL: the routes OWNED by this replica (created here). Used only by the command side; every query of the
 * REST API reads the read model instead ({@code query.ReplicatedRouteRepository}).
 */
public interface FlightRouteRepository extends JpaRepository<FlightRoute, String> {

    // Demo bootstrap: do not insert the same demo route twice (file database survives restarts)
    @Query("SELECT COUNT(r) > 0 FROM FlightRoute r WHERE r.originIata = :origin AND r.destinationIata = :dest")
    boolean existsByOriginAndDestination(@Param("origin") String origin, @Param("dest") String dest);

    @Query("SELECT r FROM FlightRoute r WHERE r.originIata = :origin AND r.destinationIata = :dest")
    List<FlightRoute> findByOriginAndDestination(@Param("origin") String origin, @Param("dest") String dest);
}
