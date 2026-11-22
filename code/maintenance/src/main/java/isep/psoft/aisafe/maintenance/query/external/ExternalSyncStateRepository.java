package isep.psoft.aisafe.maintenance.query.external;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalSyncStateRepository extends JpaRepository<ExternalSyncState, String> {
}
