package isep.psoft.aisafe.airports.events;

import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.domain.Runway;

import java.util.List;
import java.util.Set;

/**
 * Payload de {@code AirportCreated}, {@code AirportUpdated} e {@code AirportStatusChanged}: o estado COMPLETO do
 * aeroporto depois da alteração ("state-carrying event"), para que nenhum consumidor tenha de chamar o Airports
 * por HTTP. Inclui os campos que o Flight Routes lê ({@code iataCode}, {@code latitude}, {@code longitude},
 * {@code status}, {@code version}); os restantes alimentam o read model das réplicas do Airports. Os consumidores
 * ignoram os campos que não conhecem (tolerant reader).
 *
 * @param version {@code @Version} do aeroporto no write model da réplica dona (eventos mais antigos são ignorados)
 */
public record AirportEventPayload(
        String iataCode,
        String name,
        String city,
        String country,
        String region,
        String timezone,
        Double latitude,
        Double longitude,
        String status,
        List<RunwayData> runways,
        Set<String> certifiedModels,
        Integer terminals,
        Integer gates,
        Set<String> services,
        List<String> photoUrls,
        String operationalHours,
        String contactEmail,
        String contactPhone,
        Long version) {

    public AirportEventPayload {
        runways = runways == null ? List.of() : List.copyOf(runways);
        certifiedModels = certifiedModels == null ? Set.of() : Set.copyOf(certifiedModels);
        services = services == null ? Set.of() : Set.copyOf(services);
        photoUrls = photoUrls == null ? List.of() : List.copyOf(photoUrls);
    }

    /** Estado de um aeroporto do write model, depois do flush (para que {@code version} seja o definitivo). */
    public static AirportEventPayload of(Airport a) {
        return new AirportEventPayload(
                a.getIataCode(), a.getName(), a.getCity(), a.getCountry(), a.getRegion(), a.getTimezone(),
                a.getCoordinates().getLatitude(), a.getCoordinates().getLongitude(), a.getStatus().name(),
                a.getRunways().stream().map(RunwayData::of).toList(),
                a.getCertifiedModels(), a.getTerminals(), a.getGates(), a.getServices(), a.getPhotoUrls(),
                a.getOperationalHours(), a.getContactEmail(), a.getContactPhone(), a.getVersion());
    }

    /** Uma pista, tal como vai no evento. */
    public record RunwayData(String name, Integer lengthMeters, Integer orientation, String surface) {

        static RunwayData of(Runway r) {
            return new RunwayData(r.getName(), r.getLengthMeters(), r.getOrientation(), r.getSurface());
        }
    }
}
