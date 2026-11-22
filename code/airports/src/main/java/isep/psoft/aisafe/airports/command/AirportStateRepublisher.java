package isep.psoft.aisafe.airports.command;

import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.repositories.AirportRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AirportStateRepublisher {
    private static final Logger log = LoggerFactory.getLogger(AirportStateRepublisher.class);

    private final AirportRepository airportRepository;
    private final AirportEventEmitter events;

    @Transactional
    public int republishOwnedAirports(String reason) {
        List<Airport> owned = airportRepository.findAllByOrderByIataCodeAsc();
        owned.forEach(events::airportUpdated);
        log.info("Republished the state of {} owned airport(s) ({})", owned.size(), reason);
        return owned.size();
    }
}
