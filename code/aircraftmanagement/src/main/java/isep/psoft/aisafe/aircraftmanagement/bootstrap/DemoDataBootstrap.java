package isep.psoft.aisafe.aircraftmanagement.bootstrap;

import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftStatus;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftModelRepository;
import isep.psoft.aisafe.aircraftmanagement.repositories.AircraftRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Dados de arranque por réplica (cada réplica carrega dados DIFERENTES para demonstrar o P2P).
 * Modelos:   modelName:manufacturer:seats:fuelCapacity:maximumRange:cruisingSpeed
 * Aeronaves: registration:modelName:seats:manufacturingDate[:status]
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.bootstrap", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataBootstrap.class);

    private final AircraftModelRepository modelRepository;
    private final AircraftRepository aircraftRepository;

    @Value("${aisafe.bootstrap.models:}")
    private List<String> models;

    @Value("${aisafe.bootstrap.aircrafts:}")
    private List<String> aircrafts;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        models.stream().map(String::trim).filter(s -> !s.isEmpty()).forEach(this::loadModel);
        aircrafts.stream().map(String::trim).filter(s -> !s.isEmpty()).forEach(this::loadAircraft);
    }

    private void loadModel(String spec) {
        String[] p = spec.split(":");
        if (modelRepository.existsById(p[0])) {
            return;
        }
        modelRepository.save(new AircraftModel(p[0], p[1], Integer.parseInt(p[2]),
                Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5])));
        log.info("Demo aircraft model loaded: {}", p[0]);
    }

    private void loadAircraft(String spec) {
        String[] p = spec.split(":");
        if (aircraftRepository.existsById(p[0])) {
            return;
        }
        AircraftStatus status = p.length > 4 ? AircraftStatus.parse(p[4]) : AircraftStatus.AVAILABLE;
        aircraftRepository.save(new Aircraft(p[0], p[1], Integer.parseInt(p[2]), LocalDate.parse(p[3]), status));
        log.info("Demo aircraft loaded: {} ({})", p[0], p[1]);
    }
}
