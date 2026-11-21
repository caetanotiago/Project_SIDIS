package isep.psoft.aisafe.airports.query;

import isep.psoft.aisafe.airports.assemblers.AirportAssembler;
import isep.psoft.aisafe.airports.domain.IataCode;
import isep.psoft.aisafe.airports.dto.AirportDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Consultas de aeroportos (US107, US108) — LADO DE LEITURA do CQRS. Respondidas SÓ a partir do read model
 * {@code REPLICATED_AIRPORT} desta réplica, que tem todos os aeroportos de todas as réplicas: não há pedidos às
 * outras réplicas nem respostas parciais. Uma réplica responde mesmo com as outras em baixo; o preço é a
 * consistência eventual (uma alteração feita noutra réplica aparece aqui quando o evento chega).
 */
@Service
@RequiredArgsConstructor
public class SearchAirportsService {

    private final ReplicatedAirportRepository readModel;
    private final AirportAssembler assembler;

    /** US107 — detalhes pelo IATA (contrato B2 do Flight Routes). */
    @Transactional(readOnly = true)
    public AirportDTO getByIata(String iataCode) {
        String iata = IataCode.normalize(iataCode);
        return findByIata(iata).orElseThrow(() -> new EntityNotFoundException("Airport not found: " + iata));
    }

    @Transactional(readOnly = true)
    public Optional<AirportDTO> findByIata(String iata) {
        return readModel.findById(iata).map(assembler::toDTO);
    }

    /** US108 — pesquisa por cidade, país e/ou nome (sem filtros = todos os aeroportos), ordenada por IATA. */
    @Transactional(readOnly = true)
    public List<AirportDTO> search(String city, String country, String name) {
        String n = normalize(name);
        return readModel.search(normalize(city), normalize(country), n == null ? null : "%" + n + "%").stream()
                .map(assembler::toDTO)
                .toList();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
