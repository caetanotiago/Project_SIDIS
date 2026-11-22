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

@Service
@RequiredArgsConstructor
public class UpdateAirportService {
    private final AirportRepository airportRepository;
    private final PeerClient peerClient;
    private final AirportAssembler assembler;
    private final AirportEventEmitter events;

    @Transactional
    public AirportDTO addCertification(String iataCode, AddCertificationDTO dto) {
        return onOwner(iataCode, HttpMethod.POST, "/certifications", dto,
                airport -> airport.certifyModel(dto.aircraftModel()), events::airportUpdated);
    }

    @Transactional
    public AirportDTO changeStatus(String iataCode, UpdateAirportStatusDTO dto) {
        AirportStatus status = AirportStatus.from(dto.status());
        return onOwner(iataCode, HttpMethod.PATCH, "/status", dto,
                airport -> airport.changeStatus(status), events::airportStatusChanged);
    }

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
            String uri = "/api/airports/" + UriUtils.encodePathSegment(iata, StandardCharsets.UTF_8) + subPath;
            return peerClient.forwardToOwner(method, uri, body, AirportDTO.class)
                    .orElseThrow(() -> new EntityNotFoundException("Airport not found: " + iata));
        }

        Airport airport = local.get();
        change.accept(airport);
        Airport saved = airportRepository.saveAndFlush(airport);
        announce.apply(saved);
        return assembler.toDTO(saved);
    }
}
