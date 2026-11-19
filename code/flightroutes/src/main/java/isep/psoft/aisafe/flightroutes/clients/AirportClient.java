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
 * Access to the Airports microservice (anti-corruption layer), bridge B2.
 * <ol>
 *   <li>Local read model built from Airports events ({@link ExternalReadModels}), only when
 *       {@code aisafe.flightroutes.external-events.enabled=true};</li>
 *   <li>otherwise (or on a miss) synchronous HTTP: GET /api/airports/{iataCode} (US107).</li>
 * </ol>
 */
@Component
public class AirportClient {

    private static final String SERVICE = "Airports";

    private final ReplicatedServiceClient airportsService;
    private final ExternalReadModels local;

    public AirportClient(@Qualifier("airportsService") ReplicatedServiceClient airportsService,
                         ExternalReadModels local) {
        this.airportsService = airportsService;
        this.local = local;
    }

    // 404 -> EntityNotFoundException; no replica answers -> RemoteServiceUnavailableException (503)
    public AirportSnapshot getAirport(String iataCode) {
        String code = iataCode.toUpperCase();
        var known = local.airport(code);
        if (known.isPresent()) {
            return known.get();
        }
        AirportResponse airport = airportsService.call((rest, baseUrl) -> rest.get()
                .uri(baseUrl + "/api/airports/{iata}", code)
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

    // Fields of the AirportDetailsResponseDTO used here.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AirportResponse(String iataCode, Double latitude, Double longitude, String status) {}
}
