package isep.psoft.aisafe.aircraftmanagement.dto;

import java.util.List;

/**
 * Página estável em JSON (evita a serialização "direct" do Page do Spring, decisão Q8 do contrato).
 * A paginação faz-se em memória DEPOIS de juntar os dados de todas as réplicas
 * (Integration-Contracts §5, "Pagination").
 */
public record PageDTO<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageDTO<T> of(List<T> all, int page, int size) {
        int total = all.size();
        int from = (int) Math.min((long) page * size, total);
        int to = Math.min(from + size, total);
        int totalPages = (int) Math.ceil(total / (double) size);
        return new PageDTO<>(List.copyOf(all.subList(from, to)), page, size, total, totalPages);
    }
}
