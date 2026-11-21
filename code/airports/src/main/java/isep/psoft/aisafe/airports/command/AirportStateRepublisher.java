package isep.psoft.aisafe.airports.command;

import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.repositories.AirportRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Volta a anunciar o estado atual de TODOS os aeroportos de que esta réplica é dona, como {@code AirportUpdated}
 * (um evento novo por aeroporto, com a versão atual). Serve para pôr em dia read models que perderam eventos:
 * <ul>
 *   <li>o read model desta própria réplica (ex.: dados gravados antes de existir read model);</li>
 *   <li>réplicas cuja fila ainda não existia quando os eventos foram publicados (arranque ou réplica nova);</li>
 *   <li>outros serviços subscritos (ex. {@code KNOWN_AIRPORT} do Flight Routes).</li>
 * </ul>
 * É seguro repetir: quem já tem o aeroporto nessa versão só o volta a gravar igual, e versões mais antigas que
 * a guardada são ignoradas pelas projeções.
 */
@Service
@RequiredArgsConstructor
public class AirportStateRepublisher {

    private static final Logger log = LoggerFactory.getLogger(AirportStateRepublisher.class);

    private final AirportRepository airportRepository;
    private final AirportEventEmitter events;

    /** @return número de aeroportos anunciados */
    @Transactional
    public int republishOwnedAirports(String reason) {
        List<Airport> owned = airportRepository.findAllByOrderByIataCodeAsc();
        owned.forEach(events::airportUpdated);
        log.info("Republished the state of {} owned airport(s) ({})", owned.size(), reason);
        return owned.size();
    }
}
