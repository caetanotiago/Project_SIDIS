package isep.psoft.aisafe.flightroutes.repositories;

import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import isep.psoft.aisafe.flightroutes.dto.AircraftOperationalHoursDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;

public interface ScheduledFlightRepository extends JpaRepository<ScheduledFlight, String> {

    // US213 - voos agendados de uma aeronave (paginado)
    Page<ScheduledFlight> findByAircraftRegistration(String aircraftRegistration, Pageable pageable);

    // US212 - validação de sobreposição (mesma aeronave, mesma data e hora)
    @Query("SELECT COUNT(sf) > 0 FROM ScheduledFlight sf " +
           "WHERE sf.aircraftRegistration = :reg " +
           "AND sf.schedule.date = :date AND sf.schedule.time = :time")
    boolean existsOverlappingFlight(@Param("reg") String reg,
                                    @Param("date") LocalDate date,
                                    @Param("time") LocalTime time);

    // US206 - soma da duração dos voos por aeronave (só aeronaves com voos; o serviço
    // Aircraft Management assume 0 para as restantes).
    @Query(value = "SELECT new isep.psoft.aisafe.flightroutes.dto.AircraftOperationalHoursDTO(" +
                   "sf.aircraftRegistration, CAST(COALESCE(SUM(sf.route.estimatedFlightTime.durationMinutes), 0) AS long)) " +
                   "FROM ScheduledFlight sf GROUP BY sf.aircraftRegistration",
           countQuery = "SELECT COUNT(DISTINCT sf.aircraftRegistration) FROM ScheduledFlight sf")
    Page<AircraftOperationalHoursDTO> findOperationalHoursPerAircraft(Pageable pageable);
}
