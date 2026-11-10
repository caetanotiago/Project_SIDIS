package isep.psoft.aisafe.infrastructure.messaging;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Id of an integration event already processed by this replica (table {@code PROCESSED_EVENT}). */
@Entity
@Table(name = "PROCESSED_EVENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    private String eventId;

    private String eventType;

    private Instant processedAt;

    ProcessedEvent(String eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }
}
