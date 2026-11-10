package isep.psoft.aisafe.airports.dto;

/**
 * US210 — uma linha do ranking de aeroportos com mais rotas.
 */
public record BusiestAirportDTO(
        int rank,
        String iataCode,
        String name,
        String city,
        String country,
        long routeCount) {
}
