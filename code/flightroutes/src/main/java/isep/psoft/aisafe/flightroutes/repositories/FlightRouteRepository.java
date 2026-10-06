package isep.psoft.aisafe.flightroutes.repositories;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Acesso às rotas guardadas NESTA réplica. As consultas que devem ver as rotas de todas as réplicas
 * agregam estes resultados com os dos peers (ver RouteReplicaQueries).
 */
public interface FlightRouteRepository extends CrudRepository<FlightRoute, String> {

    // US114 - Procurar por Origem e Destino
    @Query("SELECT r FROM FlightRoute r WHERE r.originIata = :origin AND r.destinationIata = :dest")
    List<FlightRoute> findByOriginAndDestination(@Param("origin") String origin, @Param("dest") String dest);

    // US114 - Procurar apenas por Origem
    @Query("SELECT r FROM FlightRoute r WHERE r.originIata = :origin")
    List<FlightRoute> findByOrigin(@Param("origin") String origin);

    // US114 - Procurar apenas por Destino
    @Query("SELECT r FROM FlightRoute r WHERE r.destinationIata = :dest")
    List<FlightRoute> findByDestination(@Param("dest") String dest);

    // Validação na criação de rotas (evitar rotas duplicadas)
    @Query("SELECT COUNT(r) > 0 FROM FlightRoute r WHERE r.originIata = :origin AND r.destinationIata = :dest")
    boolean existsByOriginAndDestination(@Param("origin") String origin, @Param("dest") String dest);

    // US203 (WP#1) - rotas ativas cujos requisitos são cumpridos pela aeronave
    @Query("SELECT r FROM FlightRoute r WHERE r.status.state = 'ACTIVE' " +
           "AND r.requirements.minRange <= :range " +
           "AND r.requirements.minCapacity <= :capacity")
    List<FlightRoute> findCompatibleRoutes(@Param("range") Double range, @Param("capacity") Integer capacity);

    // US214, US215 e US216 - apenas rotas ativas (a "Network")
    @Query("SELECT r FROM FlightRoute r WHERE r.status.state = 'ACTIVE'")
    List<FlightRoute> findAllActive();

    // US209 - rotas que partem de ou chegam a um aeroporto (origem OU destino), independentemente do status.
    @Query("SELECT r FROM FlightRoute r WHERE r.originIata = :iata OR r.destinationIata = :iata")
    List<FlightRoute> findByOriginOrDestination(@Param("iata") String iataCode);

    // US210 - contagem de rotas agrupadas por aeroporto de origem.
    @Query("SELECT r.originIata, COUNT(r) FROM FlightRoute r GROUP BY r.originIata")
    List<Object[]> countByOrigin();

    // US210 - contagem de rotas agrupadas por aeroporto de destino.
    @Query("SELECT r.destinationIata, COUNT(r) FROM FlightRoute r GROUP BY r.destinationIata")
    List<Object[]> countByDestination();
}
