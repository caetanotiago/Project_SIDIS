package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.dto.AircraftOperationalHoursDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * US206 (query, also bridge B3d for Maintenance). The fleet comes from {@code REPLICATED_AIRCRAFT} and the flight
 * minutes from {@code KNOWN_SCHEDULED_FLIGHT} (Flight Routes events), both local. Aircraft without flights appear
 * with 0 hours.
 */
@Service
@RequiredArgsConstructor
public class CalculateOperationalHoursService {

    private final SearchAircraftsService searchService;
    private final FlightMinutesService flightMinutes;

    @Transactional(readOnly = true)
    public List<AircraftOperationalHoursDTO> calculate() {
        Map<String, Long> minutes = flightMinutes.minutesByAircraft();
        return searchService.fleet().stream()                                   // ordered by registration
                .map(a -> AircraftOperationalHoursDTO.of(a.registrationNumber(), a.modelName(),
                        minutes.getOrDefault(a.registrationNumber(), 0L)))
                .toList();
    }
}
