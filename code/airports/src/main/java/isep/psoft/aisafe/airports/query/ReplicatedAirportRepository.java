package isep.psoft.aisafe.airports.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReplicatedAirportRepository extends JpaRepository<ReplicatedAirport, String> {
    @Query("""
            select a from ReplicatedAirport a
            where (:city is null or lower(a.city) = :city)
              and (:country is null or lower(a.country) = :country)
              and (:namePattern is null or lower(a.name) like :namePattern)
            order by a.iataCode
            """)
    List<ReplicatedAirport> search(@Param("city") String city,
                                   @Param("country") String country,
                                   @Param("namePattern") String namePattern);
}
