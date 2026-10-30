package isep.psoft.aisafe.airports.assemblers;

import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.domain.Runway;
import isep.psoft.aisafe.airports.dto.AirportDTO;
import isep.psoft.aisafe.airports.dto.RunwayDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Converte o agregado Airport (domínio) no DTO da API. */
@Component
public class AirportAssembler {

    public AirportDTO toDTO(Airport a) {
        return new AirportDTO(
                a.getIataCode(),
                a.getName(),
                a.getCity(),
                a.getCountry(),
                a.getRegion(),
                a.getTimezone(),
                a.getCoordinates().getLatitude(),
                a.getCoordinates().getLongitude(),
                a.getStatus().name(),
                a.getRunways().stream().map(AirportAssembler::toDTO).toList(),
                Set.copyOf(a.getCertifiedModels()),
                a.getTerminals(),
                a.getGates(),
                Set.copyOf(a.getServices()),
                List.copyOf(a.getPhotoUrls()),
                a.getOperationalHours(),
                a.getContactEmail(),
                a.getContactPhone());
    }

    public List<AirportDTO> toDTOList(List<Airport> airports) {
        return airports.stream().map(this::toDTO).toList();
    }

    private static RunwayDTO toDTO(Runway r) {
        return new RunwayDTO(r.getName(), r.getLengthMeters(), r.getOrientation(), r.getSurface());
    }
}
