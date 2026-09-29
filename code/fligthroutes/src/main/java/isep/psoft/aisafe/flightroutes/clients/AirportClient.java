package isep.psoft.aisafe.flightroutes.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cliente HTTP do microsserviço Airports.
 * Usa o endpoint existente GET /airports/{iataCode} (US107).
 */
@Component
public class AirportClient {

    private static final String SERVICE = "Airports";

    private final RestClient restClient;

    public AirportClient(@Qualifier("airportsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // 404 → EntityNotFoundException; serviço em baixo → RemoteServiceUnavailableException
    public AirportSnapshot getAirport(String iataCode) {
        String code = iataCode.toUpperCase();
        try {
            AirportResponse airport = restClient.get()
                    .uri("/airports/{iata}", code)
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
                    .body(AirportResponse.class);
            return new AirportSnapshot(airport.iataCode(), airport.latitude(),
                    airport.longitude(), airport.status());
        } catch (RestClientException e) { // inclui ResourceAccessException (serviço em baixo)
            throw new RemoteServiceUnavailableException(SERVICE, e);
        }
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
