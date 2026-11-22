package isep.psoft.aisafe.airports.events;

import isep.psoft.aisafe.airports.domain.Airport;
import isep.psoft.aisafe.airports.domain.Runway;

import java.util.List;
import java.util.Set;

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

    public static AirportEventPayload of(Airport a) {
        return new AirportEventPayload(
                a.getIataCode(), a.getName(), a.getCity(), a.getCountry(), a.getRegion(), a.getTimezone(),
                a.getCoordinates().getLatitude(), a.getCoordinates().getLongitude(), a.getStatus().name(),
                a.getRunways().stream().map(RunwayData::of).toList(),
                a.getCertifiedModels(), a.getTerminals(), a.getGates(), a.getServices(), a.getPhotoUrls(),
                a.getOperationalHours(), a.getContactEmail(), a.getContactPhone(), a.getVersion());
    }

    public record RunwayData(String name, Integer lengthMeters, Integer orientation, String surface) {
        static RunwayData of(Runway r) {
            return new RunwayData(r.getName(), r.getLengthMeters(), r.getOrientation(), r.getSurface());
        }
    }
}
