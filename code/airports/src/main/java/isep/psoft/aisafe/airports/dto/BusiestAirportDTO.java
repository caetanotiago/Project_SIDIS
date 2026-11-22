package isep.psoft.aisafe.airports.dto;

// Linha do ranking (US210)
public record BusiestAirportDTO(
        int rank,
        String iataCode,
        String name,
        String city,
        String country,
        long routeCount) {
}
