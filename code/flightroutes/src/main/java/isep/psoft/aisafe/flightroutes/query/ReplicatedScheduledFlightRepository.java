package isep.psoft.aisafe.flightroutes.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Read model of the scheduled flights of ALL replicas, stored in THIS replica (see {@link ReplicatedScheduledFlight}). */
public interface ReplicatedScheduledFlightRepository extends JpaRepository<ReplicatedScheduledFlight, String> {

    // US213
    @Query("SELECT f FROM ReplicatedScheduledFlight f WHERE f.aircraftRegistration = :reg ORDER BY f.date, f.time, f.id")
    List<ReplicatedScheduledFlight> findByAircraftRegistration(@Param("reg") String registration);

    // US212: same aircraft at the same date and time (schedule conflict), across all replicas
    @Query("SELECT f FROM ReplicatedScheduledFlight f WHERE f.aircraftRegistration = :reg "
            + "AND f.date = :date AND f.time = :time ORDER BY f.id")
    List<ReplicatedScheduledFlight> findConflicting(@Param("reg") String registration,
                                                    @Param("date") LocalDate date,
                                                    @Param("time") LocalTime time);

    // US214 (popularity): [routeId, count]
    @Query("SELECT f.routeId, COUNT(f) FROM ReplicatedScheduledFlight f GROUP BY f.routeId")
    List<Object[]> countByRoute();

    // US206 (B4a/B4b provider): [registration, minutes]
    @Query("SELECT f.aircraftRegistration, SUM(f.estimatedFlightTime) FROM ReplicatedScheduledFlight f "
            + "GROUP BY f.aircraftRegistration")
    List<Object[]> sumMinutesByAircraft();
}
