package isep.psoft.aisafe.flightroutes.query.external;

import isep.psoft.aisafe.flightroutes.clients.AircraftSnapshot;
import isep.psoft.aisafe.flightroutes.clients.AirportSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Local copies of the aircraft and airports of the other services, used by {@code AircraftClient} and
 * {@code AirportClient} BEFORE they call the other service over HTTP. With
 * {@code aisafe.flightroutes.external-events.enabled=false} (the default, until Aircraft Management and Airports
 * publish events) it always answers "unknown", so B1 and B2 stay HTTP exactly as before.
 *
 * <p>When enabled, a miss (an aircraft or airport whose event has not arrived, e.g. data created before this replica
 * subscribed) still falls back to HTTP, so the switch never makes a request fail.
 */
@Component
public class ExternalReadModels {

    private final boolean enabled;
    private final KnownAircraftRepository aircraft;
    private final KnownAircraftModelRepository models;
    private final KnownAirportRepository airports;

    public ExternalReadModels(@Value("${aisafe.flightroutes.external-events.enabled:false}") boolean enabled,
                              KnownAircraftRepository aircraft, KnownAircraftModelRepository models,
                              KnownAirportRepository airports) {
        this.enabled = enabled;
        this.aircraft = aircraft;
        this.models = models;
        this.airports = airports;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** The aircraft with the range of its model, if both are known locally. */
    @Transactional(readOnly = true)
    public Optional<AircraftSnapshot> aircraft(String registration) {
        if (!enabled || registration == null) {
            return Optional.empty();
        }
        return aircraft.findById(registration)
                .filter(a -> a.getModelName() != null && a.getStatus() != null && a.getSeatingCapacity() != null)
                .flatMap(a -> models.findById(a.getModelName())
                        .filter(m -> m.getMaximumRange() != null)
                        .map(m -> new AircraftSnapshot(a.getRegistrationNumber(), a.getStatus(),
                                a.getSeatingCapacity(), m.getMaximumRange())));
    }

    @Transactional(readOnly = true)
    public Optional<AirportSnapshot> airport(String iataCode) {
        if (!enabled || iataCode == null) {
            return Optional.empty();
        }
        return airports.findById(iataCode.toUpperCase())
                .filter(a -> a.getLatitude() != null && a.getLongitude() != null && a.getStatus() != null)
                .map(a -> new AirportSnapshot(a.getIataCode(), a.getLatitude(), a.getLongitude(), a.getStatus()));
    }
}
