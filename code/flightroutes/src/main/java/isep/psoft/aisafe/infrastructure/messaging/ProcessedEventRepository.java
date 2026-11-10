package isep.psoft.aisafe.infrastructure.messaging;

import org.springframework.data.repository.CrudRepository;

public interface ProcessedEventRepository extends CrudRepository<ProcessedEvent, String> {
}
