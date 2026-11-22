package isep.psoft.aisafe.maintenance.query.external;

import isep.psoft.aisafe.maintenance.clients.AircraftClient;
import isep.psoft.aisafe.maintenance.clients.AircraftClient.Fleet;
import isep.psoft.aisafe.maintenance.clients.AircraftClient.FlightHours;
import isep.psoft.aisafe.maintenance.clients.AircraftSnapshot;
import isep.sidis.common.remote.RemoteServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FleetDirectory {

    private static final Logger log = LoggerFactory.getLogger(FleetDirectory.class);

    private final KnownAircraftRepository aircraft;
    private final KnownAircraftModelRepository models;
    private final KnownScheduledFlightRepository flights;
    private final ExternalSyncStateRepository syncState;
    private final AircraftClient aircraftClient;
    private final Clock clock = Clock.systemUTC();

    public FleetDirectory(KnownAircraftRepository aircraft, KnownAircraftModelRepository models,
                          KnownScheduledFlightRepository flights, ExternalSyncStateRepository syncState,
                          AircraftClient aircraftClient) {
        this.aircraft = aircraft;
        this.models = models;
        this.flights = flights;
        this.syncState = syncState;
        this.aircraftClient = aircraftClient;
    }

    @Transactional
    public AircraftSnapshot aircraft(String registration) {
        return aircraft.findByRegistrationNumberIgnoreCase(registration)
                .map(KnownAircraft::toSnapshot)
                .orElseGet(() -> {
                    AircraftSnapshot remote = aircraftClient.getAircraft(registration);
                    remember(remote);
                    log.info("Aircraft {} not in the local copy yet: read from Aircraft Management (B3a)", registration);
                    return remote;
                });
    }

    @Transactional
    public boolean modelExists(String modelName) {
        if (models.existsByModelNameIgnoreCase(modelName)) {
            return true;
        }
        boolean exists = aircraftClient.modelExists(modelName);
        if (exists && !models.existsById(modelName)) {
            KnownAircraftModel row = new KnownAircraftModel(modelName);
            row.apply(null, null, KnownAircraft.FROM_HTTP, clock.instant());
            models.save(row);
            log.info("Aircraft model {} not in the local copy yet: confirmed by Aircraft Management (B3c)", modelName);
        }
        return exists;
    }

    @Transactional
    public Fleet fleet() {
        boolean partial = false;
        if (!syncState.existsById(ExternalSyncState.FLEET)) {
            try {
                Fleet remote = aircraftClient.fleet();
                remote.aircraft().forEach(this::remember);
                syncState.save(new ExternalSyncState(ExternalSyncState.FLEET, clock.instant(), remote.aircraft().size()));
                log.info("Local fleet copy seeded once from Aircraft Management (B3d): {} aircraft",
                        remote.aircraft().size());
                partial = remote.partial();
            } catch (RemoteServiceUnavailableException | ResponseStatusException e) {
                log.warn("Fleet copy not seeded yet and Aircraft Management unavailable ({}): using the local copy",
                        e.getMessage());
                partial = true;
            }
        }
        List<AircraftSnapshot> fleet = aircraft.findAllByOrderByRegistrationNumberAsc().stream()
                .map(KnownAircraft::toSnapshot)
                .toList();
        return new Fleet(fleet, partial);
    }

    @Transactional(readOnly = true)
    public FlightHours flightHours() {
        if (flights.count() == 0) {
            try {
                return aircraftClient.flightHours();
            } catch (RemoteServiceUnavailableException | ResponseStatusException e) {
                log.warn("No flights received yet and Aircraft Management unavailable ({}): only calendar-day alerts",
                        e.getMessage());
                return new FlightHours(Map.of(), true);
            }
        }
        Map<String, Double> hours = new HashMap<>();
        for (Object[] row : flights.sumMinutesByAircraft()) {
            if (row[0] != null && row[1] != null) {
                hours.put((String) row[0], Math.round(((Number) row[1]).doubleValue() / 60.0 * 100.0) / 100.0);
            }
        }
        return new FlightHours(hours, false);
    }

    private void remember(AircraftSnapshot snapshot) {
        if (snapshot == null || snapshot.registrationNumber() == null) {
            return;
        }
        if (aircraft.findByRegistrationNumberIgnoreCase(snapshot.registrationNumber()).isEmpty()) {
            aircraft.save(KnownAircraft.fromHttp(snapshot, clock.instant()));
        }
    }
}
