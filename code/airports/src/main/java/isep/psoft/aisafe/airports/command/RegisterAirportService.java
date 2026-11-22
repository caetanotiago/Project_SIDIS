package isep.psoft.aisafe.airports.command;

import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.domain.IataCode;
import isep.psoft.aisafe.airports.dto.CreateAirportDTO;
import isep.psoft.aisafe.airports.factories.AirportFactory;
import isep.psoft.aisafe.airports.repositories.AirportRepository;
import isep.psoft.aisafe.airports.query.ReplicatedAirportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterAirportService {
    private final AirportRepository airportRepository;
    private final ReplicatedAirportRepository readModel;
    private final AirportFactory airportFactory;
    private final AirportEventEmitter events;

    @Transactional
    public Airport register(CreateAirportDTO dto) {
        String iata = IataCode.normalize(dto.iataCode());

        if (airportRepository.existsById(iata) || readModel.existsById(iata)) {
            throw new IllegalStateException("Airport already registered: " + iata);
        }

        Airport saved = airportRepository.saveAndFlush(airportFactory.create(dto));
        events.airportCreated(saved);
        return saved;
    }
}
