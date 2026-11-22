package isep.psoft.aisafe.aircraftmanagement.query;

import isep.psoft.aisafe.aircraftmanagement.dto.AircraftMoodDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@code GET /api/v2/unicorns} (query): the mood of every aircraft, read ONLY from the projection
 * {@code AIRCRAFT_MOOD}. A pure query: it never touches the write model and keeps answering while the owner replicas
 * of the aircraft are down (it answers from this replica's copy). docs/2-containers/Unicorns-Mock.md.
 */
@Service
@RequiredArgsConstructor
public class AircraftMoodQueryService {

    private final AircraftMoodRepository moods;

    @Transactional(readOnly = true)
    public List<AircraftMoodDTO> moods() {
        return moods.findAllOrdered().stream()
                .map(m -> new AircraftMoodDTO(m.getRegistration(), m.getModel(), m.getOperationalStatus(),
                        m.getMood(), m.getUpdatedAt(), AircraftMoodDTO.SOURCE_READ_MODEL))
                .toList();
    }
}
