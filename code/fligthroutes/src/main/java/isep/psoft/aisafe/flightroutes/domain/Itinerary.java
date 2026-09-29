package isep.psoft.aisafe.flightroutes.domain;

import lombok.Getter;

import java.util.List;

/**
 * Read result (US216): an alternative route between two airports, modelled as an
 * ordered chain of existing FlightRoute legs (one or more stopovers). Derived from
 * the connectivity graph of active routes; not a persisted entity.
 */
@Getter
public class Itinerary {

    private final List<FlightRoute> legs;
    private final int numberOfStops;
    private final double totalDistance;

    public Itinerary(List<FlightRoute> legs) {
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("An itinerary must have at least one leg.");
        }
        this.legs = List.copyOf(legs);
        this.numberOfStops = legs.size() - 1; // direct route => 0 stops
        this.totalDistance = legs.stream()
                .mapToDouble(r -> r.getDistance().getDistance())
                .sum();
    }
}
