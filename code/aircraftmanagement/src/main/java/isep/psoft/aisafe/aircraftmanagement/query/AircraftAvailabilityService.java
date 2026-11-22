package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftAvailabilityDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.FleetAvailabilityDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * US205 (query): availability of one aircraft and of the fleet, from this replica's read model. "Real time" means
 * eventually consistent: on the owner replica the status is current as soon as US105 commits; on the other replicas
 * it is current once the {@code AircraftStatusChanged} event has been applied (typically milliseconds).
 */
@Service
@RequiredArgsConstructor
public class AircraftAvailabilityService {

    private final SearchAircraftsService searchService;

    @Transactional(readOnly = true)
    public AircraftAvailabilityDTO forAircraft(String registrationNumber) {
        AircraftSummary a = searchService.getByRegistration(registrationNumber);
        return new AircraftAvailabilityDTO(a.registrationNumber(), a.status().name(),
                a.status() == AircraftStatus.AVAILABLE);
    }

    @Transactional(readOnly = true)
    public FleetAvailabilityDTO forFleet() {
        List<AircraftSummary> fleet = searchService.fleet();

        Map<String, Long> counts = new LinkedHashMap<>();
        for (AircraftStatus s : AircraftStatus.values()) {
            counts.put(s.name(), 0L);
        }
        fleet.forEach(a -> counts.merge(a.status().name(), 1L, Long::sum));

        long total = fleet.size();
        double rate = total == 0 ? 0.0
                : Math.round(counts.get(AircraftStatus.AVAILABLE.name()) * 100.0 / total) / 100.0;
        return new FleetAvailabilityDTO(counts, total, rate);
    }
}
