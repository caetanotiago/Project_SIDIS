package isep.psoft.aisafe.flightroutes.repositories;

import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * WRITE MODEL: the scheduled flights OWNED by this replica (created here). Used only by the command side; the
 * queries read {@code query.ReplicatedScheduledFlightRepository}.
 */
public interface ScheduledFlightRepository extends JpaRepository<ScheduledFlight, String> {

    // Demo bootstrap: do not insert the same demo flight twice
    @Query("SELECT sf FROM ScheduledFlight sf WHERE sf.aircraftRegistration = :reg "
            + "AND sf.schedule.date = :date AND sf.schedule.time = :time")
    List<ScheduledFlight> findConflicting(@Param("reg") String reg,
                                          @Param("date") LocalDate date,
                                          @Param("time") LocalTime time);
}
