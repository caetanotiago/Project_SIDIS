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

// Consultas: US107, US108
@Service
@RequiredArgsConstructor
public class SearchAirportsService {
    private final ReplicatedAirportRepository readModel;
    private final AirportAssembler assembler;

    // Por IATA ou 404
    @Transactional(readOnly = true)
    public AirportDTO getByIata(String iataCode) {
        String iata = IataCode.normalize(iataCode);
        return findByIata(iata).orElseThrow(() -> new EntityNotFoundException("Airport not found: " + iata));
    }

    // Por IATA (opcional)
    @Transactional(readOnly = true)
    public Optional<AirportDTO> findByIata(String iata) {
        return readModel.findById(iata).map(assembler::toDTO);
    }

    // Pesquisa com filtros
    @Transactional(readOnly = true)
    public List<AirportDTO> search(String city, String country, String name) {
        String n = normalize(name);
        return readModel.search(normalize(city), normalize(country), n == null ? null : "%" + n + "%").stream()
                .map(assembler::toDTO)
                .toList();
    }

    // Filtro em minúsculas
    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
