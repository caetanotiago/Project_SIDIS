package isep.psoft.aisafe.flightroutes.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Paginação em memória de resultados agregados de várias réplicas: cada réplica tem só uma parte
 * dos dados, por isso a página só pode ser cortada depois de juntar e ordenar tudo.
 */
final class Pages {

    private Pages() {
    }

    static <T> Page<T> of(List<T> all, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(all);
        }
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }
}
