package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * US103 (bridges B1a, B3a) and US104 (query). Answered from THIS replica's read model {@code REPLICATED_AIRCRAFT},
 * which holds the aircraft of every replica: no peer call, no aggregation, never {@code X-Partial-Response}
 * (Assignment 1 asked every peer on every GET).
 */
@Service
@RequiredArgsConstructor
public class SearchAircraftsService {

    private final ReplicatedAircraftRepository aircraft;

    @Transactional(readOnly = true)
    public AircraftSummary getByRegistration(String registrationNumber) {
        return aircraft.findById(registrationNumber).map(ReplicatedAircraft::toSummary)
                .orElseThrow(() -> new EntityNotFoundException("Aircraft not found: " + registrationNumber));
    }

    /**
     * US104: search by model, status and/or manufacturing year. Optional filters, combined with AND.
     * Returns the whole fleet that matches, ordered by registration (the controller pages it).
     */
    @Transactional(readOnly = true)
    public List<AircraftSummary> search(String modelName, String status, Integer manufacturingYear) {
        AircraftStatus statusFilter = (status == null || status.isBlank()) ? null : AircraftStatus.parse(status);
        return aircraft.findAllOrdered().stream()
                .map(ReplicatedAircraft::toSummary)
                .filter(a -> modelName == null || modelName.isBlank() || modelName.equals(a.modelName()))
                .filter(a -> statusFilter == null || a.status() == statusFilter)
                .filter(a -> manufacturingYear == null
                        || (a.manufacturingDate() != null && a.manufacturingDate().getYear() == manufacturingYear))
                .toList();
    }

    /** The whole fleet (every replica), ordered by registration. */
    @Transactional(readOnly = true)
    public List<AircraftSummary> fleet() {
        return search(null, null, null);
    }
}
