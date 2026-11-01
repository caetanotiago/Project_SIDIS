package isep.psoft.aisafe.aircraftmanagement.dto;

import java.util.List;

/**
 * Página com os mesmos nomes de campos do Page "direct" do Spring Data usado no Flight Routes
 * (Integration-Contracts §2.6): content, totalElements, totalPages, size, number, numberOfElements,
 * first, last, empty. A paginação faz-se em memória DEPOIS de juntar os dados de todas as réplicas (§5).
 */
public record PageDTO<T>(List<T> content, long totalElements, int totalPages, int size, int number,
                         int numberOfElements, boolean first, boolean last, boolean empty) {

    public static <T> PageDTO<T> of(List<T> all, int page, int size) {
        int total = all.size();
        int from = (int) Math.min((long) page * size, total);
        int to = Math.min(from + size, total);
        List<T> slice = List.copyOf(all.subList(from, to));
        int totalPages = (int) Math.ceil(total / (double) size);
        return new PageDTO<>(slice, total, totalPages, size, page, slice.size(),
                page == 0, page >= totalPages - 1, slice.isEmpty());
    }
}
