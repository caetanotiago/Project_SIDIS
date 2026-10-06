package isep.psoft.aisafe.flightroutes.repositories;

import isep.psoft.aisafe.flightroutes.domain.ScheduledFlight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Acesso aos voos agendados guardados NESTA réplica (agregados com os peers na camada de serviço).
 */
public interface ScheduledFlightRepository extends JpaRepository<ScheduledFlight, String> {

    // US213 - voos agendados de uma aeronave
    List<ScheduledFlight> findByAircraftRegistration(String aircraftRegistration);

    // US212 - voos da mesma aeronave à mesma data e hora (conflito de agendamento)
    @Query("SELECT sf FROM ScheduledFlight sf " +
           "WHERE sf.aircraftRegistration = :reg " +
           "AND sf.schedule.date = :date AND sf.schedule.time = :time")
    List<ScheduledFlight> findConflicting(@Param("reg") String reg,
                                          @Param("date") LocalDate date,
                                          @Param("time") LocalTime time);

    // US214 - nº de voos por rota (popularidade): [routeId, count]
    @Query("SELECT sf.route.routeId, COUNT(sf) FROM ScheduledFlight sf GROUP BY sf.route.routeId")
    List<Object[]> countByRoute();

    // US206 - minutos de voo por aeronave: [registration, minutes]
    @Query("SELECT sf.aircraftRegistration, SUM(sf.route.estimatedFlightTimeMinutes) " +
           "FROM ScheduledFlight sf GROUP BY sf.aircraftRegistration")
    List<Object[]> sumMinutesByAircraft();
}
