package isep.psoft.aisafe.flightroutes.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import isep.sidis.common.remote.ReplicatedServiceClient;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cliente HTTP do microsserviço Airports (anti-corruption layer).
 * Usa o endpoint existente GET /airports/{iataCode} (US107).
 */
@Component
public class AirportClient {

    private static final String SERVICE = "Airports";

    private final ReplicatedServiceClient airportsService;

    public AirportClient(@Qualifier("airportsService") ReplicatedServiceClient airportsService) {
        this.airportsService = airportsService;
    }

    // 404 → EntityNotFoundException; nenhuma réplica responde → RemoteServiceUnavailableException (503)
    public AirportSnapshot getAirport(String iataCode) {
        String code = iataCode.toUpperCase();
        AirportResponse airport = airportsService.call((rest, baseUrl) -> rest.get()
                .uri(baseUrl + "/airports/{iata}", code)
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, res) -> {
                    throw new EntityNotFoundException("Airport not found: " + code);
                })
                .onStatus(s -> s.value() == 400, (req, res) -> {
                    throw new IllegalArgumentException("Invalid IATA code: " + code);
                })
                .onStatus(s -> s.value() == 401 || s.value() == 403, (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.valueOf(res.getStatusCode().value()),
                            SERVICE + " rejected the request.");
                })
                .body(AirportResponse.class));
        return new AirportSnapshot(airport.iataCode(), airport.latitude(),
                airport.longitude(), airport.status());
    }

    public boolean exists(String iataCode) {
        try {
            getAirport(iataCode);
            return true;
        } catch (EntityNotFoundException e) {
            return false;
        }
    }

    // Campos do AirportDetailsResponseDTO que nos interessam.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AirportResponse(String iataCode, Double latitude, Double longitude, String status) {}
}
