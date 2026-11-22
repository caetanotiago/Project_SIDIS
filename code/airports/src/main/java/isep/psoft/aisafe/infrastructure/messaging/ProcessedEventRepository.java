package isep.psoft.aisafe.infrastructure.messaging;

import org.springframework.data.repository.CrudRepository;

// Acesso a PROCESSED_EVENT
public interface ProcessedEventRepository extends CrudRepository<ProcessedEvent, String> {
}
