package isep.psoft.aisafe.airports.repositories;

import isep.psoft.aisafe.airports.domain.Airport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Aeroportos guardados NESTA réplica. Os dados das outras réplicas são obtidos por REST
 * (ver AirportReplicaQueries).
 */
public interface AirportRepository extends JpaRepository<Airport, String> {

    /**
     * US108 — pesquisa por cidade, país e/ou nome; parâmetros a null são ignorados.
     * Cidade e país: igualdade sem distinguir maiúsculas. Nome: "contém".
     * Os valores chegam já em minúsculas (e o nome já como padrão LIKE) a partir do serviço,
     * para não aplicar funções a parâmetros que podem ser null.
     */
    @Query("""
            select a from Airport a
            where (:city is null or lower(a.city) = :city)
              and (:country is null or lower(a.country) = :country)
              and (:namePattern is null or lower(a.name) like :namePattern)
            order by a.iataCode
            """)
    List<Airport> search(@Param("city") String city,
                         @Param("country") String country,
                         @Param("namePattern") String namePattern);

    List<Airport> findAllByOrderByIataCodeAsc();
}
