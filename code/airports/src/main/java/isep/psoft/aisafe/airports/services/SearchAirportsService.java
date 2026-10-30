package isep.psoft.aisafe.airports.services;

import isep.psoft.aisafe.airports.domain.IataCode;
import isep.psoft.aisafe.airports.dto.AirportDTO;
import isep.psoft.aisafe.airports.repositories.AirportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

import static isep.psoft.aisafe.airports.services.AirportReplicaQueries.uri;

/** Consultas de aeroportos (US107, US108), sempre sobre os dados de todas as réplicas. */
@Service
@RequiredArgsConstructor
public class SearchAirportsService {

    private final AirportRepository airportRepository;
    private final AirportReplicaQueries replicas;

    /**
     * US107 — detalhes pelo IATA: local, senão encaminhado aos peers (o primeiro 200 ganha).
     * Nenhuma réplica o tem → 404. Também é o endpoint do contrato B2 usado pelo Flight Routes.
     */
    @Transactional(readOnly = true)
    public AirportDTO getByIata(String iataCode) {
        String iata = IataCode.normalize(iataCode);
        return replicas.findByIata(iata)
                .orElseThrow(() -> new EntityNotFoundException("Airport not found: " + iata));
    }

    /**
     * US108 — pesquisa por cidade, país e/ou nome (sem filtros = todos os aeroportos).
     * Os peers recebem os mesmos filtros e respondem só com os seus dados locais.
     */
    @Transactional(readOnly = true)
    public List<AirportDTO> search(String city, String country, String name) {
        String c = normalize(city);
        String co = normalize(country);
        String n = normalize(name);
        String namePattern = n == null ? null : "%" + n + "%";

        return replicas.withPeers(
                airportRepository.search(c, co, namePattern),
                uri("/api/airports", "city", city, "country", country, "name", name));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
