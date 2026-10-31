package isep.psoft.aisafe.maintenance.dto;

import java.util.List;

/**
 * Página com formato estável (o mesmo do Aircraft Management). A página é cortada em memória DEPOIS
 * de juntar os dados de todas as réplicas (Integration-Contracts §5, "Pagination").
 */
public record PageDTO<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static <T> PageDTO<T> of(List<T> all, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and " + MAX_SIZE);
        }
        int total = all.size();
        int from = (int) Math.min((long) page * size, total);
        int to = Math.min(from + size, total);
        int totalPages = (int) Math.ceil(total / (double) size);
        return new PageDTO<>(List.copyOf(all.subList(from, to)), page, size, total, totalPages);
    }
}
