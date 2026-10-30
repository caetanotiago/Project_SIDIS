package isep.psoft.aisafe.airports.bootstrap;

import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.dto.CreateAirportDTO;
import isep.psoft.aisafe.airports.dto.RunwayDTO;
import isep.psoft.aisafe.airports.factories.AirportFactory;
import isep.psoft.aisafe.airports.repositories.AirportRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dados de demonstração (perfil {@code demo}). Cada réplica carrega um conjunto DIFERENTE de aeroportos
 * (lista em {@code aisafe.bootstrap.airports} no seu application-instanceN.properties), para demonstrar
 * que os dados estão particionados pelas réplicas e que o encaminhamento peer-to-peer os encontra.
 *
 * <p>São os aeroportos usados pelas rotas de demonstração do Flight Routes (OPO, LIS, MAD, CDG, LHR,
 * FNC), por isso a US110/US212 e a US210 funcionam de ponta a ponta com os dois perfis demo ativos.
 *
 * <pre>
 * aisafe.bootstrap.airports=OPO,LIS
 * </pre>
 *
 * Os aeroportos são gravados diretamente no repositório local, sem consultar os peers (no arranque
 * os peers podem ainda não estar disponíveis).
 */
@Component
@ConditionalOnProperty(prefix = "aisafe.bootstrap", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataBootstrap.class);

    /** Catálogo de aeroportos de demonstração (dados reais aproximados). */
    private static final Map<String, CreateAirportDTO> CATALOG = Map.of(
            "OPO", airport("OPO", "Francisco Sá Carneiro Airport", "Porto", "Portugal", "Europe/Lisbon",
                    41.2481, -8.6814, List.of(new RunwayDTO("17/35", 3480, 170, "ASPHALT")),
                    1, 37, Set.of("WIFI", "LOUNGE", "CAR_RENTAL"), "00:00-24:00", "info.porto@ana.pt"),
            "LIS", airport("LIS", "Humberto Delgado Airport", "Lisbon", "Portugal", "Europe/Lisbon",
                    38.7742, -9.1342, List.of(new RunwayDTO("02/20", 3805, 20, "ASPHALT"),
                            new RunwayDTO("17/35", 2400, 170, "ASPHALT")),
                    2, 60, Set.of("WIFI", "LOUNGE", "METRO"), "00:00-24:00", "info.lisboa@ana.pt"),
            "MAD", airport("MAD", "Adolfo Suárez Madrid-Barajas Airport", "Madrid", "Spain", "Europe/Madrid",
                    40.4719, -3.5626, List.of(new RunwayDTO("14L/32R", 3500, 140, "ASPHALT"),
                            new RunwayDTO("18R/36L", 4350, 180, "ASPHALT")),
                    4, 230, Set.of("WIFI", "LOUNGE", "METRO", "TRAIN"), "00:00-24:00", null),
            "CDG", airport("CDG", "Paris Charles de Gaulle Airport", "Paris", "France", "Europe/Paris",
                    49.0097, 2.5479, List.of(new RunwayDTO("08L/26R", 4215, 80, "ASPHALT"),
                            new RunwayDTO("09R/27L", 4200, 90, "ASPHALT")),
                    3, 300, Set.of("WIFI", "LOUNGE", "TRAIN"), "00:00-24:00", null),
            "LHR", airport("LHR", "London Heathrow Airport", "London", "United Kingdom", "Europe/London",
                    51.4700, -0.4543, List.of(new RunwayDTO("09L/27R", 3902, 90, "ASPHALT"),
                            new RunwayDTO("09R/27L", 3660, 90, "ASPHALT")),
                    4, 115, Set.of("WIFI", "LOUNGE", "TRAIN"), "06:00-23:30", null),
            "FNC", airport("FNC", "Madeira Airport", "Funchal", "Portugal", "Atlantic/Madeira",
                    32.6979, -16.7745, List.of(new RunwayDTO("05/23", 2781, 50, "ASPHALT")),
                    1, 13, Set.of("WIFI"), "06:00-24:00", null));

    /** Modelos certificados em todos os aeroportos de demonstração (US106a). */
    private static final List<String> DEMO_CERTIFIED_MODELS = List.of("A320", "A321");

    private final AirportRepository airportRepository;
    private final AirportFactory airportFactory;

    @Value("${aisafe.bootstrap.airports:}")
    private List<String> airports;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String code : airports) {
            if (code.isBlank()) {
                continue;
            }
            String iata = code.trim().toUpperCase(Locale.ROOT);
            CreateAirportDTO dto = CATALOG.get(iata);
            if (dto == null) {
                log.warn("Demo airport {} skipped: not in the demo catalog {}", iata, CATALOG.keySet());
                continue;
            }
            if (airportRepository.existsById(iata)) {
                continue;
            }
            Airport airport = airportFactory.create(dto);
            DEMO_CERTIFIED_MODELS.forEach(airport::certifyModel);
            airportRepository.save(airport);
            log.info("Demo airport loaded: {} ({}, {})", iata, dto.city(), dto.country());
        }
    }

    private static CreateAirportDTO airport(String iata, String name, String city, String country, String timezone,
                                            double lat, double lon, List<RunwayDTO> runways,
                                            int terminals, int gates, Set<String> services,
                                            String hours, String email) {
        return new CreateAirportDTO(iata, name, city, country, "Europe", timezone, lat, lon, runways,
                terminals, gates, services, List.of(), hours, email, null);
    }
}
