package isep.psoft.aisafe.aircraftmanagement.bootstrap;

import isep.psoft.aisafe.aircraftmanagement.command.AircraftManagementEventEmitter;
import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Demo data ({@code demo} profile), as in Flight Routes. Each instance loads a DIFFERENT set of models and aircraft
 * (from its application-instanceN.properties), so the demo shows that every replica owns part of the data and that
 * the events replicate it to the read models of all the others (and to Flight Routes / any other subscriber).
 *
 * <pre>
 * aisafe.bootstrap.models=A320:Airbus:180:24210:6100:828        (modelName:manufacturer:seats:fuel:maximumRange:cruisingSpeed)
 * aisafe.bootstrap.aircrafts=CS-TUA:A320:180:2019-05-14[:STATUS] (registration:modelName:seats:manufacturingDate[:status])
 * </pre>
 *
 * <ul>
 *   <li>Runs on {@link ApplicationReadyEvent}: the RabbitMQ listener containers are already started, so this
 *       replica's own queues exist and the events reach every replica that is up.</li>
 *   <li>Idempotent: with the file database the data survives a restart, so a row that already exists is skipped and
 *       NO event is published for it. Only the replica that inserts a row publishes its event.</li>
 *   <li>Each inserted row goes through {@link AircraftManagementEventEmitter}, like any other write: it is applied to
 *       this replica's read model at once and published after the commit.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.bootstrap", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DemoDataBootstrap.class);

    private final AircraftModelRepository modelRepository;
    private final AircraftRepository aircraftRepository;
    private final AircraftManagementEventEmitter events;

    @Value("${aisafe.bootstrap.models:}")
    private List<String> models;

    @Value("${aisafe.bootstrap.aircrafts:}")
    private List<String> aircrafts;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)   // before ReadModelSynchronizer (@Order(2)), which then announces the state this replica owns
    @Transactional
    public void load() {
        int inserted = 0;
        for (String spec : models) {
            if (!spec.isBlank() && loadModel(spec.trim())) {
                inserted++;
            }
        }
        for (String spec : aircrafts) {
            if (!spec.isBlank() && loadAircraft(spec.trim())) {
                inserted++;
            }
        }
        log.info("Demo data: {} new row(s) inserted (existing rows are kept and not republished)", inserted);
    }

    private boolean loadModel(String spec) {
        String[] p = spec.split(":");
        if (modelRepository.existsById(p[0])) {
            return false;
        }
        AircraftModel model = modelRepository.saveAndFlush(new AircraftModel(p[0], p[1], Integer.parseInt(p[2]),
                Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5])));
        events.aircraftModelCreated(model);
        log.info("Demo aircraft model loaded: {}", model.getModelName());
        return true;
    }

    private boolean loadAircraft(String spec) {
        String[] p = spec.split(":");
        if (aircraftRepository.existsById(p[0])) {
            return false;
        }
        AircraftStatus status = p.length > 4 ? AircraftStatus.parse(p[4]) : AircraftStatus.AVAILABLE;
        Aircraft aircraft = aircraftRepository.saveAndFlush(
                new Aircraft(p[0], p[1], Integer.parseInt(p[2]), LocalDate.parse(p[3]), status));
        events.aircraftRegistered(aircraft);
        log.info("Demo aircraft loaded: {} ({})", aircraft.getRegistrationNumber(), aircraft.getModelName());
        return true;
    }
}
