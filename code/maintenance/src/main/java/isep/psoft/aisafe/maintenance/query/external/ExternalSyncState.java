package isep.psoft.aisafe.maintenance.query.external;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "EXTERNAL_SYNC_STATE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalSyncState {

    public static final String FLEET = "aircraftmanagement-fleet";

    @Id
    private String name;

    private Instant syncedAt;
    private int entries;

    ExternalSyncState(String name, Instant syncedAt, int entries) {
        this.name = name;
        this.syncedAt = syncedAt;
        this.entries = entries;
    }
}
