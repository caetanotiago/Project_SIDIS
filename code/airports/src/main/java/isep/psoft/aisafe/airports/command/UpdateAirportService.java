package isep.psoft.aisafe.airports.command;

import isep.psoft.aisafe.airports.assemblers.AirportAssembler;
import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.domain.AirportStatus;
import isep.psoft.aisafe.airports.domain.IataCode;
import isep.psoft.aisafe.airports.dto.AddCertificationDTO;
import isep.psoft.aisafe.airports.dto.AirportDTO;
import isep.psoft.aisafe.airports.dto.UpdateAirportDetailsDTO;
import isep.psoft.aisafe.airports.dto.UpdateAirportStatusDTO;
import isep.psoft.aisafe.airports.repositories.AirportRepository;
import isep.sidis.common.peers.PeerClient;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * US106a, US109, US208 (comandos) — alterar um aeroporto existente. UM DONO POR ENTIDADE: a escrita é sempre
 * feita pela réplica que tem o aeroporto no seu write model. Se não for local, o comando é reencaminhado (unicast)
 * para a réplica dona, por isso nunca há duas versões do mesmo aeroporto e cada aeroporto tem um único produtor
 * de eventos. O {@code @Version} da réplica dona deteta escritas concorrentes (409).
 */
@Service
@RequiredArgsConstructor
public class UpdateAirportService {

    private final AirportRepository airportRepository;
    private final PeerClient peerClient;
    private final AirportAssembler assembler;
    private final AirportEventEmitter events;

    /** US106a — certificar um modelo de aeronave para operar no aeroporto ({@code AirportUpdated}). */
    @Transactional
    public AirportDTO addCertification(String iataCode, AddCertificationDTO dto) {
        return onOwner(iataCode, HttpMethod.POST, "/certifications", dto,
                airport -> airport.certifyModel(dto.aircraftModel()), events::airportUpdated);
    }

    /** US109 — alterar o estado operacional ({@code AirportStatusChanged}). */
    @Transactional
    public AirportDTO changeStatus(String iataCode, UpdateAirportStatusDTO dto) {
        AirportStatus status = AirportStatus.from(dto.status()); // valida antes de encaminhar (400)
        return onOwner(iataCode, HttpMethod.PATCH, "/status", dto,
                airport -> airport.changeStatus(status), events::airportStatusChanged);
    }

    /** US208 — atualizar horário, contactos e instalações, parcialmente ({@code AirportUpdated}). */
    @Transactional
    public AirportDTO updateDetails(String iataCode, UpdateAirportDetailsDTO dto) {
        return onOwner(iataCode, HttpMethod.PATCH, "", dto,
                airport -> airport.updateDetails(dto.operationalHours(), dto.contactEmail(), dto.contactPhone(),
                        dto.terminals(), dto.gates(), dto.services(), dto.photoUrls()),
                events::airportUpdated);
    }

    private AirportDTO onOwner(String iataCode, HttpMethod method, String subPath, Object body,
                               Consumer<Airport> change, Function<Airport, ?> announce) {
        String iata = IataCode.normalize(iataCode);
        Optional<Airport> local = airportRepository.findById(iata);

        if (local.isEmpty()) {
            // Não sou o dono: reencaminho o comando. Quem publica o evento é a réplica dona.
            String uri = "/api/airports/" + UriUtils.encodePathSegment(iata, StandardCharsets.UTF_8) + subPath;
            return peerClient.forwardToOwner(method, uri, body, AirportDTO.class)
                    .orElseThrow(() -> new EntityNotFoundException("Airport not found: " + iata));
        }

        Airport airport = local.get();
        change.accept(airport);
        // Flush primeiro: o evento leva a versão incrementada por esta alteração.
        Airport saved = airportRepository.saveAndFlush(airport);
        announce.apply(saved);
        return assembler.toDTO(saved);
    }
}
