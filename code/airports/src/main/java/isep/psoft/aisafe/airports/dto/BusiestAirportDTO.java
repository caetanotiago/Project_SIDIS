package isep.psoft.aisafe.airports.dto;

/**
 * US210 — uma linha do ranking de aeroportos com mais rotas.
 * {@code name}, {@code city} e {@code country} ficam a null se o aeroporto não for encontrado
 * em nenhuma réplica do Airports (ex. réplica dona em baixo — a resposta vem marcada como parcial).
 */
public record BusiestAirportDTO(
        int rank,
        String iataCode,
        String name,
        String city,
        String country,
        long routeCount) {
}
