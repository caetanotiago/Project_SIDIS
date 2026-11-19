package isep.psoft.aisafe.flightroutes.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read model of the routes of ALL replicas, stored in THIS replica (see {@link ReplicatedRoute}). Every route query
 * of the REST API is answered from here; nothing is aggregated with the peers.
 */
public interface ReplicatedRouteRepository extends JpaRepository<ReplicatedRoute, String> {

    @Query("SELECT r FROM ReplicatedRoute r ORDER BY r.originIata, r.destinationIata, r.id")
    List<ReplicatedRoute> findAllOrdered();

    // US114
    @Query("SELECT r FROM ReplicatedRoute r WHERE r.originIata = :origin AND r.destinationIata = :dest ORDER BY r.id")
    List<ReplicatedRoute> findByOriginAndDestination(@Param("origin") String origin, @Param("dest") String dest);

    @Query("SELECT r FROM ReplicatedRoute r WHERE r.originIata = :origin ORDER BY r.destinationIata, r.id")
    List<ReplicatedRoute> findByOrigin(@Param("origin") String origin);

    @Query("SELECT r FROM ReplicatedRoute r WHERE r.destinationIata = :dest ORDER BY r.originIata, r.id")
    List<ReplicatedRoute> findByDestination(@Param("dest") String dest);

    // US110: one route per origin -> destination (checked on the read model, i.e. against every replica)
    @Query("SELECT COUNT(r) > 0 FROM ReplicatedRoute r WHERE r.originIata = :origin AND r.destinationIata = :dest")
    boolean existsByOriginAndDestination(@Param("origin") String origin, @Param("dest") String dest);

    // US203 (B6 provider): active routes whose requirements are met by the aircraft
    @Query("SELECT r FROM ReplicatedRoute r WHERE r.status = 'ACTIVE' AND r.minRange <= :range "
            + "AND r.minCapacity <= :capacity ORDER BY r.originIata, r.destinationIata, r.id")
    List<ReplicatedRoute> findCompatibleRoutes(@Param("range") Double range, @Param("capacity") Integer capacity);

    // US214, US215, US216: the network = active routes
    @Query("SELECT r FROM ReplicatedRoute r WHERE r.status = 'ACTIVE' ORDER BY r.originIata, r.destinationIata, r.id")
    List<ReplicatedRoute> findAllActive();

    // US209
    @Query("SELECT r FROM ReplicatedRoute r WHERE r.originIata = :iata OR r.destinationIata = :iata "
            + "ORDER BY r.originIata, r.destinationIata, r.id")
    List<ReplicatedRoute> findByOriginOrDestination(@Param("iata") String iataCode);

    // US210 (B5 provider): [iata, count]
    @Query("SELECT r.originIata, COUNT(r) FROM ReplicatedRoute r GROUP BY r.originIata")
    List<Object[]> countByOrigin();

    @Query("SELECT r.destinationIata, COUNT(r) FROM ReplicatedRoute r GROUP BY r.destinationIata")
    List<Object[]> countByDestination();
}
