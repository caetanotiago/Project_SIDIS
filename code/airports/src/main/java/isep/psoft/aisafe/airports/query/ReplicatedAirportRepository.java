package isep.psoft.aisafe.airports.query;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Acesso ao read model {@code REPLICATED_AIRPORT} desta réplica. */
public interface ReplicatedAirportRepository extends JpaRepository<ReplicatedAirport, String> {

    List<ReplicatedAirport> findAllByOrderByIataCodeAsc();
}
