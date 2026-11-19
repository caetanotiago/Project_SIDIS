package isep.psoft.aisafe.flightroutes.bootstrap;

import isep.psoft.aisafe.flightroutes.command.FlightRoutesEventEmitter;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Demo data ({@code demo} profile). Each instance loads a DIFFERENT set of routes and flights (from its
 * application-instanceN.properties), so the demo shows that every replica owns part of the data and that the
 * events replicate it to the read models of all the others. Distances are given directly, so Airports is not needed.
 *
 * <pre>
 * aisafe.bootstrap.routes=OPO-LIS:274:55:400:120      (origin-destination:km:minutes:minRange:minCapacity)
 * aisafe.bootstrap.flights=CS-TUA@OPO-LIS@2026-11-02T08:00   (registration@route@date-time)
 * </pre>
 *
 * <ul>
 *   <li>Runs on {@link ApplicationReadyEvent}: the RabbitMQ listener containers are already started, so this
 *       replica's own queue exists and the events reach every replica that is up.</li>
 *   <li>Idempotent: with the file database the data survives a restart, so a row that already exists is
 *       skipped and NO event is published for it. Only the replica that inserts a row publishes its event.</li>
 *   <li>Each inserted row goes through {@link FlightRoutesEventEmitter}, like any other write: it is applied to
 *       this replica's read model at once and published after the commit.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.bootstrap", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DemoDataBootstrap.class);

    private final FlightRouteRepository routeRepository;
    private final ScheduledFlightRepository flightRepository;
    private final FlightRouteFactory routeFactory;
    private final ScheduledFlightFactory flightFactory;
    private final FlightRoutesEventEmitter events;

    @Value("${aisafe.bootstrap.routes:}")
    private List<String> routes;

    @Value("${aisafe.bootstrap.flights:}")
    private List<String> flights;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void load() {
        int inserted = 0;
        for (String spec : routes) {
            if (!spec.isBlank() && loadRoute(spec.trim())) {
                inserted++;
            }
        }
        for (String spec : flights) {
            if (!spec.isBlank() && loadFlight(spec.trim())) {
                inserted++;
            }
        }
        log.info("Demo data: {} new row(s) inserted (existing rows are kept and not republished)", inserted);
    }

    private boolean loadRoute(String spec) {
        String[] parts = spec.split(":");
        String[] airports = parts[0].split("-");
        if (routeRepository.existsByOriginAndDestination(airports[0], airports[1])) {
            return false;
        }
        FlightRoute route = routeRepository.saveAndFlush(routeFactory.createRoute(airports[0], airports[1],
                Double.parseDouble(parts[1]), Double.parseDouble(parts[3]),
                Integer.parseInt(parts[4]), Integer.parseInt(parts[2])));
        events.routeCreated(route);
        log.info("Demo route loaded: {} -> {} (id {})", route.getOriginIata(), route.getDestinationIata(), route.getId());
        return true;
    }

    private boolean loadFlight(String spec) {
        String[] parts = spec.split("@");
        String[] airports = parts[1].split("-");
        LocalDateTime when = LocalDateTime.parse(parts[2]);
        if (!flightRepository.findConflicting(parts[0], when.toLocalDate(), when.toLocalTime()).isEmpty()) {
            return false;
        }
        return routeRepository.findByOriginAndDestination(airports[0], airports[1]).stream().findFirst()
                .map(route -> {
                    var flight = flightRepository.saveAndFlush(flightFactory.create(parts[0], route.toSummary(),
                            new FlightSchedule(when.toLocalDate(), when.toLocalTime())));
                    events.scheduledFlightCreated(flight);
                    log.info("Demo flight loaded: {} on {} at {}", parts[0], parts[1], when);
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("Demo flight {} skipped: route {} is not stored in this replica", spec, parts[1]);
                    return false;
                });
    }
}
