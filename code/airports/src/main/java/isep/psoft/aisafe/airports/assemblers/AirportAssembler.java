package isep.psoft.aisafe.airports.assemblers;

import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.domain.Runway;
import isep.psoft.aisafe.airports.dto.AirportDTO;
import isep.psoft.aisafe.airports.dto.RunwayDTO;
import isep.psoft.aisafe.airports.query.ReplicatedAirport;
import isep.psoft.aisafe.airports.query.ReplicatedRunway;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

// Conversão para DTO
@Component
public class AirportAssembler {
    // Write model → DTO
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

    // Read model → DTO
    public AirportDTO toDTO(ReplicatedAirport a) {
        return new AirportDTO(
                a.getIataCode(),
                a.getName(),
                a.getCity(),
                a.getCountry(),
                a.getRegion(),
                a.getTimezone(),
                a.getLatitude(),
                a.getLongitude(),
                a.getStatus(),
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

    // Pista → DTO
    private static RunwayDTO toDTO(Runway r) {
        return new RunwayDTO(r.getName(), r.getLengthMeters(), r.getOrientation(), r.getSurface());
    }

    // Pista replicada → DTO
    private static RunwayDTO toDTO(ReplicatedRunway r) {
        return new RunwayDTO(r.getName(), r.getLengthMeters(), r.getOrientation(), r.getSurface());
    }
}
