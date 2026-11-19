package isep.psoft.aisafe.flightroutes.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates of the scheduled flights of ALL replicas, from this replica's flight read model.
 * <ul>
 *   <li>{@link #usageByRoute()} - number of flights per route (popularity, US214);</li>
 *   <li>{@link #minutesByAircraft()} - flight minutes per aircraft (operational hours, US206; B4a/B4b provider).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ScheduledFlightStatisticsService {

    private final ReplicatedScheduledFlightRepository flights;

    @Transactional(readOnly = true)
    public Map<String, Long> usageByRoute() {
        return toMap(flights.countByRoute());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> minutesByAircraft() {
        return toMap(flights.sumMinutesByAircraft());
    }

    private static Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> totals = new HashMap<>();
        for (Object[] row : rows) {
            totals.merge((String) row[0], ((Number) row[1]).longValue(), Long::sum);
        }
        return totals;
    }
}
