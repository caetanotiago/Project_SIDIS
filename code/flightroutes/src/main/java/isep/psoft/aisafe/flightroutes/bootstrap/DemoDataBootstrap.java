package isep.psoft.aisafe.flightroutes.bootstrap;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.domain.FlightSchedule;
import isep.psoft.aisafe.flightroutes.factories.FlightRouteFactory;
import isep.psoft.aisafe.flightroutes.factories.ScheduledFlightFactory;
import isep.psoft.aisafe.flightroutes.repositories.FlightRouteRepository;
import isep.psoft.aisafe.flightroutes.repositories.ScheduledFlightRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dados de demonstração (perfil {@code demo}). Cada instância carrega um conjunto DIFERENTE de rotas
 * e voos (definido no seu application-instanceN.properties), para demonstrar que os dados estão
 * particionados pelas réplicas e que o encaminhamento peer-to-peer os encontra.
 * As distâncias são dadas diretamente, por isso não é preciso o serviço Airports.
 *
 * <pre>
 * aisafe.bootstrap.routes=OPO-LIS:274:55:400:120      (origem-destino:km:minutos:alcanceMin:capacidadeMin)
 * aisafe.bootstrap.flights=CS-TUA@OPO-LIS@2026-11-02T08:00   (matrícula@rota@data-hora)
 * </pre>
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.bootstrap", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataBootstrap.class);

    private final FlightRouteRepository routeRepository;
    private final ScheduledFlightRepository flightRepository;
    private final FlightRouteFactory routeFactory;
    private final ScheduledFlightFactory flightFactory;

    @Value("${aisafe.bootstrap.routes:}")
    private List<String> routes;

    @Value("${aisafe.bootstrap.flights:}")
    private List<String> flights;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String spec : routes) {
            if (!spec.isBlank()) {
                loadRoute(spec.trim());
            }
        }
        for (String spec : flights) {
            if (!spec.isBlank()) {
                loadFlight(spec.trim());
            }
        }
    }

    private void loadRoute(String spec) {
        String[] parts = spec.split(":");
        String[] airports = parts[0].split("-");
        if (routeRepository.existsByOriginAndDestination(airports[0], airports[1])) {
            return;
        }
        FlightRoute route = routeRepository.save(routeFactory.createRoute(airports[0], airports[1],
                Double.parseDouble(parts[1]), Double.parseDouble(parts[3]),
                Integer.parseInt(parts[4]), Integer.parseInt(parts[2])));
        log.info("Demo route loaded: {} -> {} (id {})", route.getOriginIata(), route.getDestinationIata(), route.getId());
    }

    private void loadFlight(String spec) {
        String[] parts = spec.split("@");
        String[] airports = parts[1].split("-");
        LocalDateTime when = LocalDateTime.parse(parts[2]);
        routeRepository.findByOriginAndDestination(airports[0], airports[1]).stream().findFirst()
                .ifPresentOrElse(route -> {
                    flightRepository.save(flightFactory.create(parts[0], route.toSummary(),
                            new FlightSchedule(when.toLocalDate(), when.toLocalTime())));
                    log.info("Demo flight loaded: {} on {} at {}", parts[0], parts[1], when);
                }, () -> log.warn("Demo flight {} skipped: route {} is not stored in this replica", spec, parts[1]));
    }
}
