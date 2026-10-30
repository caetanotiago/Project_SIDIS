package isep.psoft.aisafe.maintenance.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import isep.sidis.common.remote.ReplicatedServiceClient;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTP anti-corruption layer for Aircraft Management.
 *
 * @author Diogo Fernandes - 1240632
 */
@Component
public class AircraftClient {

    private final ReplicatedServiceClient aircraftService;
    private final boolean validationEnabled;

    public AircraftClient(
            @Qualifier("aircraftService") ReplicatedServiceClient aircraftService,
            @Value("${maintenance.aircraft-validation.enabled:true}") boolean validationEnabled) {
        this.aircraftService = aircraftService;
        this.validationEnabled = validationEnabled;
    }

    public AircraftDTO getAircraft(String registrationNumber) {
        if (!validationEnabled) {
            return new AircraftDTO(registrationNumber, null, null, "AVAILABLE");
        }
        try {
            return aircraftService.call((rest, baseUrl) -> rest.get()
                    .uri(baseUrl + "/api/aircrafts/{registrationNumber}", registrationNumber)
                    .retrieve()
                    .body(AircraftDTO.class));
        } catch (HttpClientErrorException.NotFound exception) {
            throw new EntityNotFoundException("Aircraft not found: " + registrationNumber);
        } catch (HttpClientErrorException exception) {
            throw propagateClientError(exception);
        }
    }

    public AircraftDTO updateStatus(String registrationNumber, String status) {
        if (!"UNDER_MAINTENANCE".equals(status) && !"AVAILABLE".equals(status)) {
            throw new IllegalArgumentException("Unsupported maintenance aircraft status: " + status);
        }
        if (!validationEnabled) {
            return new AircraftDTO(registrationNumber, null, null, status);
        }

        try {
            return aircraftService.call((rest, baseUrl) -> rest.method(HttpMethod.PATCH)
                    .uri(baseUrl + "/api/aircrafts/{registrationNumber}/status", registrationNumber)
                    .body(new StatusUpdateRequest(status))
                    .retrieve()
                    .body(AircraftDTO.class));
        } catch (HttpClientErrorException.NotFound exception) {
            throw new EntityNotFoundException("Aircraft not found: " + registrationNumber);
        } catch (HttpClientErrorException exception) {
            throw propagateClientError(exception);
        }
    }

    private static ResponseStatusException propagateClientError(HttpClientErrorException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return new ResponseStatusException(status, exception.getStatusText(), exception);
    }

    /**
     * Aircraft data consumed from the Aircraft Management B1a contract.
     *
     * @author Diogo Fernandes - 1240632
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AircraftDTO(
            String registrationNumber,
            String modelName,
            Integer seatingCapacity,
            String status) {
    }

    /**
     * Request body for the Aircraft Management B3b status update.
     *
     * @author Diogo Fernandes - 1240632
     */
    private record StatusUpdateRequest(String status) {
    }
}
