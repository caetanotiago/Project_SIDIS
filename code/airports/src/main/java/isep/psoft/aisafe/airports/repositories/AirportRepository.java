package isep.psoft.aisafe.airports.repositories;

import isep.psoft.aisafe.airports.domain.Airport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Acesso ao write model
public interface AirportRepository extends JpaRepository<Airport, String> {
    List<Airport> findAllByOrderByIataCodeAsc();
}
