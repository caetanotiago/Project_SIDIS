package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.query.external.KnownScheduledFlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flight minutes per aircraft (registration -> minutes), computed from the local read model
 * {@code KNOWN_SCHEDULED_FLIGHT} fed by Flight Routes' {@code ScheduledFlightCreated} events. Replaces the HTTP
 * bridge B4b ({@code GET /api/scheduled-flights/statistics/minutes-by-aircraft}): same numbers, but Flight Routes
 * does not have to be up, and the answer is never partial.
 */
@Service
@RequiredArgsConstructor
public class FlightMinutesService {

    private final KnownScheduledFlightRepository flights;

    @Transactional(readOnly = true)
    public Map<String, Long> minutesByAircraft() {
        Map<String, Long> totals = new HashMap<>();
        for (Object[] row : flights.sumMinutesByAircraft()) {
            if (row[0] != null && row[1] != null) {
                totals.merge((String) row[0], ((Number) row[1]).longValue(), Long::sum);
            }
        }
        return totals;
    }
}
