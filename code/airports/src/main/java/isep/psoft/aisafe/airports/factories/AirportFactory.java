package isep.psoft.aisafe.airports.factories;

import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.domain.Coordinates;
import isep.psoft.aisafe.airports.domain.Runway;
import isep.psoft.aisafe.airports.dto.CreateAirportDTO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory: cria um Airport válido a partir do pedido de registo (US106 + US207 + contactos US208).
 * As regras de negócio ficam no domínio; aqui só se monta o agregado e os value objects.
 */
@Component
public class AirportFactory {

    public Airport create(CreateAirportDTO dto) {
        List<Runway> runways = dto.runways().stream()
                .map(r -> new Runway(r.name(), r.lengthMeters(), r.orientation(), r.surface()))
                .toList();

        Airport airport = new Airport(
                dto.iataCode(), dto.name(), dto.city(), dto.country(), dto.region(), dto.timezone(),
                new Coordinates(dto.latitude(), dto.longitude()), runways);

        airport.describeFacilities(dto.terminals(), dto.gates(), dto.services(), dto.photoUrls());
        airport.updateContactDetails(dto.operationalHours(), dto.contactEmail(), dto.contactPhone());
        return airport;
    }
}
