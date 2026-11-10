package isep.psoft.aisafe.airports.services;

import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.domain.IataCode;
import isep.psoft.aisafe.airports.dto.CreateAirportDTO;
import isep.psoft.aisafe.airports.factories.AirportFactory;
import isep.psoft.aisafe.airports.repositories.AirportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US106 / US207 — registar um aeroporto. Fica guardado na réplica que recebeu o pedido
 */
@Service
@RequiredArgsConstructor
public class RegisterAirportService {

    private final AirportRepository airportRepository;
    private final AirportReplicaQueries replicas;
    private final AirportFactory airportFactory;

    @Transactional
    public Airport register(CreateAirportDTO dto) {
        String iata = IataCode.normalize(dto.iataCode());

        if (replicas.findByIata(iata).isPresent()) {
            throw new IllegalStateException("Airport already registered: " + iata);
        }

        return airportRepository.save(airportFactory.create(dto));
    }
}
