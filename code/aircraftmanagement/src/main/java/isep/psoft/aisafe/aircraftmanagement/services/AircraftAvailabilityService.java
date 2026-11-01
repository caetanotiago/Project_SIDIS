package isep.psoft.aisafe.aircraftmanagement.services;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftAvailabilityDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.FleetAvailabilityDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * US205. "Tempo real" = o estado é lido no momento do pedido da réplica dona da aeronave
 * (sem cache); o estado é alterado pelo PATCH /status (US105 / ponte B3b).
 */
@Service
@RequiredArgsConstructor
public class AircraftAvailabilityService {

    private final SearchAircraftsService searchService;

    public AircraftAvailabilityDTO forAircraft(String registrationNumber) {
        AircraftSummary a = searchService.getByRegistration(registrationNumber);
        return new AircraftAvailabilityDTO(a.registrationNumber(), a.status().name(),
                a.status() == AircraftStatus.AVAILABLE);
    }

    public FleetAvailabilityDTO forFleet() {
        List<AircraftSummary> fleet = searchService.search(null, null, null);

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
