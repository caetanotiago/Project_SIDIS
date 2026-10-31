package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient;
import isep.psoft.aisafe.aircraftmanagement.clients.FlightRoutesClient.MinutesByAircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftOperationalHoursDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * US206. A frota vem do Aircraft Management (todas as réplicas, via pesquisa agregada) e os minutos
 * de voo vêm do Flight Routes (ponte B4b). As aeronaves sem voos aparecem com 0 horas.
 */
@Service
@RequiredArgsConstructor
public class CalculateOperationalHoursService {

    private final SearchAircraftsService searchService;
    private final FlightRoutesClient flightRoutesClient;

    public record Result(List<AircraftOperationalHoursDTO> rows, boolean partial) { }

    public Result calculate() {
        List<AircraftSummary> fleet = searchService.search(null, null, null); // ordenada por matrícula
        MinutesByAircraft usage = flightRoutesClient.minutesByAircraft();

        List<AircraftOperationalHoursDTO> rows = fleet.stream()
                .map(a -> AircraftOperationalHoursDTO.of(a.registrationNumber(), a.modelName(),
                        usage.minutes().getOrDefault(a.registrationNumber(), 0L)))
                .toList();
        return new Result(rows, usage.partial());
    }
}
