package isep.psoft.aisafe.aircraftmanagement.query.external;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KnownScheduledFlightRepository extends JpaRepository<KnownScheduledFlight, String> {

    /** US204 / US206: [registration, minutes] - the same sum Flight Routes returns in bridge B4b. */
    @Query("SELECT f.aircraftRegistration, SUM(f.estimatedFlightTime) FROM KnownScheduledFlight f "
            + "GROUP BY f.aircraftRegistration")
    List<Object[]> sumMinutesByAircraft();
}
