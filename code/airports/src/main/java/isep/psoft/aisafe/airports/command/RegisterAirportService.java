package isep.psoft.aisafe.airports.command;

import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.domain.IataCode;
import isep.psoft.aisafe.airports.dto.CreateAirportDTO;
import isep.psoft.aisafe.airports.factories.AirportFactory;
import isep.psoft.aisafe.airports.repositories.AirportRepository;
import isep.psoft.aisafe.airports.services.AirportReplicaQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US106 / US207 (comando) — registar um aeroporto. Fica guardado no write model da réplica que recebeu o pedido,
 * que passa a ser a sua DONA, e é anunciado a todas as réplicas e serviços com {@code AirportCreated}.
 */
@Service
@RequiredArgsConstructor
public class RegisterAirportService {

    private final AirportRepository airportRepository;
    private final AirportReplicaQueries replicas;
    private final AirportFactory airportFactory;
    private final AirportEventEmitter events;

    @Transactional
    public Airport register(CreateAirportDTO dto) {
        String iata = IataCode.normalize(dto.iataCode());

        if (replicas.findByIata(iata).isPresent()) {
            throw new IllegalStateException("Airport already registered: " + iata);
        }

        // Flush primeiro: o evento leva a versão definitiva do aeroporto.
        Airport saved = airportRepository.saveAndFlush(airportFactory.create(dto));
        events.airportCreated(saved);
        return saved;
    }
}
