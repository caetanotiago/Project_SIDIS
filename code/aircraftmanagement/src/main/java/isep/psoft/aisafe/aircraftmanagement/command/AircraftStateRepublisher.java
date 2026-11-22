package isep.psoft.aisafe.aircraftmanagement.command;

import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Announces again the CURRENT state of every model and aircraft that THIS replica owns, as
 * {@code AircraftModelCreated} / {@code AircraftRegistered} (one new event per entity, with its current version) —
 * the same mechanism as {@code AirportStateRepublisher} in Airports. It brings up to date read models that missed
 * events:
 * <ul>
 *   <li>this replica's own read model;</li>
 *   <li>replicas whose queue did not exist yet when the events were published (start-up, new replica);</li>
 *   <li>other subscribed services (Flight Routes {@code KNOWN_AIRCRAFT*}), and events lost while the broker was down
 *       (there is no outbox).</li>
 * </ul>
 * Safe to repeat: a consumer that already has the entity in that version just writes it again, and versions older
 * than the stored one are ignored by the projections.
 */
@Service
@RequiredArgsConstructor
public class AircraftStateRepublisher {

    private static final Logger log = LoggerFactory.getLogger(AircraftStateRepublisher.class);

    private final AircraftModelRepository models;
    private final AircraftRepository aircraft;
    private final AircraftManagementEventEmitter events;

    /** @return number of entities (models + aircraft) announced */
    @Transactional
    public int republishOwnedState(String reason) {
        List<AircraftModel> ownedModels = models.findAll();
        List<Aircraft> ownedAircraft = aircraft.findAll();
        ownedModels.forEach(events::aircraftModelCreated);
        ownedAircraft.forEach(events::aircraftRegistered);
        log.info("Republished the state of {} owned model(s) and {} owned aircraft ({})",
                ownedModels.size(), ownedAircraft.size(), reason);
        return ownedModels.size() + ownedAircraft.size();
    }
}
