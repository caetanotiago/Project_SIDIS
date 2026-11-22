package isep.psoft.aisafe.maintenance.query.external;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KnownScheduledFlightRepository extends JpaRepository<KnownScheduledFlight, String> {

    @Query("select upper(f.aircraftRegistration), sum(f.estimatedFlightTime) from KnownScheduledFlight f "
            + "where f.aircraftRegistration is not null group by upper(f.aircraftRegistration)")
    List<Object[]> sumMinutesByAircraft();
}
