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
 * Cliente HTTP do microsserviço Aircraft Management.
 * Usa os endpoints existentes: GET /api/aircrafts/{registrationNumber} (US103)
 * e GET /api/aircraft-models/{modelName} (para obter o alcance máximo do modelo).
 */
@Component
public class AircraftClient {

    private static final String SERVICE = "Aircraft Management";

    private final RestClient restClient;

    public AircraftClient(@Qualifier("aircraftRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // 404 → EntityNotFoundException; serviço em baixo → RemoteServiceUnavailableException
    public AircraftSnapshot getAircraft(String registration) {
        AircraftResponse aircraft = get("/api/aircrafts/{reg}", AircraftResponse.class,
                "Aircraft not found: " + registration, registration);
        AircraftModelResponse model = get("/api/aircraft-models/{model}", AircraftModelResponse.class,
                "Aircraft model not found: " + aircraft.modelName(), aircraft.modelName());

        return new AircraftSnapshot(aircraft.registrationNumber(), aircraft.status(),
                aircraft.seatingCapacity(), model.maximumRange());
    }

    public boolean exists(String registration) {
        try {
            get("/api/aircrafts/{reg}", AircraftResponse.class, "", registration);
            return true;
        } catch (EntityNotFoundException e) {
            return false;
        }
    }

    private <T> T get(String uri, Class<T> type, String notFoundMessage, Object... uriVariables) {
        try {
            return restClient.get()
                    .uri(uri, uriVariables)
                    .retrieve()
                    .onStatus(s -> s.value() == 404, (req, res) -> {
                        throw new EntityNotFoundException(notFoundMessage);
                    })
                    .onStatus(s -> s.value() == 401 || s.value() == 403, (req, res) -> {
                        throw new ResponseStatusException(HttpStatus.valueOf(res.getStatusCode().value()),
                                SERVICE + " rejected the request.");
                    })
                    .body(type);
        } catch (RestClientException e) { // inclui ResourceAccessException (serviço em baixo)
            throw new RemoteServiceUnavailableException(SERVICE, e);
        }
    }

    // Campos do AircraftDTO que nos interessam (os restantes, incluindo _links, são ignorados).
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AircraftResponse(String registrationNumber, String modelName,
                                    Integer seatingCapacity, String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AircraftModelResponse(String modelName, Double maximumRange) {}
}
