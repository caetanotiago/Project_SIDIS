package isep.psoft.aisafe.flightroutes.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import isep.psoft.aisafe.flightroutes.query.external.ExternalReadModels;
import isep.sidis.common.remote.ReplicatedServiceClient;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Access to the Aircraft Management microservice (anti-corruption layer), bridge B1.
 * <ol>
 *   <li>Local read model built from Aircraft Management events ({@link ExternalReadModels}), only when
 *       {@code aisafe.flightroutes.external-events.enabled=true};</li>
 *   <li>otherwise (or on a miss) synchronous HTTP: GET /api/aircrafts/{registrationNumber} (US103) and
 *       GET /api/aircraft-models/{modelName} (maximum range of the model).</li>
 * </ol>
 */
@Component
public class AircraftClient {

    private static final String SERVICE = "Aircraft Management";

    private final ReplicatedServiceClient aircraftService;
    private final ExternalReadModels local;

    public AircraftClient(@Qualifier("aircraftService") ReplicatedServiceClient aircraftService,
                          ExternalReadModels local) {
        this.aircraftService = aircraftService;
        this.local = local;
    }

    // 404 -> EntityNotFoundException; no replica answers -> RemoteServiceUnavailableException (503)
    public AircraftSnapshot getAircraft(String registration) {
        var known = local.aircraft(registration);
        if (known.isPresent()) {
            return known.get();
        }
        AircraftResponse aircraft = get("/api/aircrafts/{reg}", AircraftResponse.class,
                "Aircraft not found: " + registration, registration);
        AircraftModelResponse model = get("/api/aircraft-models/{model}", AircraftModelResponse.class,
                "Aircraft model not found: " + aircraft.modelName(), aircraft.modelName());

        return new AircraftSnapshot(aircraft.registrationNumber(), aircraft.status(),
                aircraft.seatingCapacity(), model.maximumRange());
    }

    public boolean exists(String registration) {
        if (local.aircraft(registration).isPresent()) {
            return true;
        }
        try {
            get("/api/aircrafts/{reg}", AircraftResponse.class, "", registration);
            return true;
        } catch (EntityNotFoundException e) {
            return false;
        }
    }

    private <T> T get(String uri, Class<T> type, String notFoundMessage, Object... uriVariables) {
        return aircraftService.call((rest, baseUrl) -> rest.get()
                .uri(baseUrl + uri, uriVariables)
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, res) -> {
                    throw new EntityNotFoundException(notFoundMessage);
                })
                .onStatus(s -> s.value() == 401 || s.value() == 403, (req, res) -> {
                    throw new ResponseStatusException(HttpStatus.valueOf(res.getStatusCode().value()),
                            SERVICE + " rejected the request.");
                })
                .body(type));
    }

    // Fields of the AircraftDTO used here (the others, including _links, are ignored).
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AircraftResponse(String registrationNumber, String modelName,
                                    Integer seatingCapacity, String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AircraftModelResponse(String modelName, Double maximumRange) {}
}
