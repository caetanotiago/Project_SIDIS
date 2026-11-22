package isep.psoft.aisafe.aircraftmanagement.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Read model of {@code GET /api/v2/unicorns} (see {@link AircraftMood}). */
public interface AircraftMoodRepository extends JpaRepository<AircraftMood, String> {

    @Query("SELECT m FROM AircraftMood m ORDER BY m.registration")
    List<AircraftMood> findAllOrdered();
}
