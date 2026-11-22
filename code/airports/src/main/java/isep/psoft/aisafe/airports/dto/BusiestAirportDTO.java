package isep.psoft.aisafe.airports.dto;

public record BusiestAirportDTO(
        int rank,
        String iataCode,
        String name,
        String city,
        String country,
        long routeCount) {
}
