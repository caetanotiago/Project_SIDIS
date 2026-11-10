package isep.psoft.aisafe.maintenance.services;

import isep.psoft.aisafe.maintenance.assemblers.MaintenanceRecordAssembler;
import isep.psoft.aisafe.maintenance.assemblers.MaintenanceTemplateAssembler;
import isep.psoft.aisafe.maintenance.domain.MaintenanceTemplate;
import isep.psoft.aisafe.maintenance.dto.MaintenanceRecordDTO;
import isep.psoft.aisafe.maintenance.dto.MaintenanceTemplateDTO;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceRecordRepository;
import isep.psoft.aisafe.maintenance.repositories.MaintenanceTemplateRepository;
import isep.sidis.common.peers.PeerClient;
import isep.sidis.common.peers.PeerResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class MaintenanceReplicaQueries {

    static final ParameterizedTypeReference<List<MaintenanceRecordDTO>> RECORD_LIST = new ParameterizedTypeReference<>() { };
    static final ParameterizedTypeReference<List<MaintenanceTemplateDTO>> TEMPLATE_LIST = new ParameterizedTypeReference<>() { };

    static final Comparator<MaintenanceRecordDTO> MOST_RECENT_FIRST =
            Comparator.comparing(MaintenanceRecordDTO::getStartDate, Comparator.reverseOrder())
                    .thenComparing(MaintenanceRecordDTO::getId);

    private final MaintenanceRecordRepository recordRepository;
    private final MaintenanceTemplateRepository templateRepository;
    private final MaintenanceRecordAssembler recordAssembler;
    private final MaintenanceTemplateAssembler templateAssembler;
    private final PeerClient peerClient;

    public record RecordSearch(List<MaintenanceRecordDTO> records, boolean complete) { }

    public Optional<MaintenanceRecordDTO> findRecord(String id) {
        return recordRepository.findById(id)
                .map(recordAssembler::toDTO)
                .or(() -> peerClient.findFirst("/api/maintenance-records/" + pathSegment(id), MaintenanceRecordDTO.class)
                        .map(recordAssembler::withLinks));
    }

    public List<MaintenanceRecordDTO> searchRecords(RecordFilter filter) {
        return searchRecordsWithStatus(filter).records();
    }

    public RecordSearch searchRecordsWithStatus(RecordFilter filter) {
        Map<String, MaintenanceRecordDTO> merged = new LinkedHashMap<>();
        recordRepository.findAll().stream()
                .filter(filter::matches)
                .forEach(r -> merged.put(r.getId(), recordAssembler.toDTO(r)));

        PeerResult<List<MaintenanceRecordDTO>> peers = peerClient.collect(filter.toUri(), RECORD_LIST);
        peers.responses().forEach(list -> list.forEach(dto ->
                merged.putIfAbsent(dto.getId(), recordAssembler.withLinks(dto))));

        List<MaintenanceRecordDTO> sorted = merged.values().stream().sorted(MOST_RECENT_FIRST).toList();
        return new RecordSearch(sorted, !peers.isPartial());
    }

    public Optional<MaintenanceTemplateDTO> findTemplate(String id) {
        return templateRepository.findById(id)
                .map(templateAssembler::toDTO)
                .or(() -> peerClient.findFirst("/api/maintenance-templates/" + pathSegment(id), MaintenanceTemplateDTO.class)
                        .map(templateAssembler::withLinks));
    }

    public List<MaintenanceTemplateDTO> listTemplates(Predicate<MaintenanceTemplate> localFilter, String peerUri) {
        Map<String, MaintenanceTemplateDTO> merged = new LinkedHashMap<>();
        templateRepository.findAll().stream()
                .filter(localFilter)
                .forEach(t -> merged.put(t.getId(), templateAssembler.toDTO(t)));
        peerClient.collectList(peerUri, TEMPLATE_LIST).forEach(dto ->
                merged.putIfAbsent(dto.getId(), templateAssembler.withLinks(dto)));
        return merged.values().stream()
                .sorted(Comparator.comparing(MaintenanceTemplateDTO::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public boolean isPeerRequest() {
        return peerClient.isPeerRequest();
    }

    static String uri(String path, Object... nameValuePairs) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(path);
        for (int i = 0; i + 1 < nameValuePairs.length; i += 2) {
            Object value = nameValuePairs[i + 1];
            if (value != null && !value.toString().isBlank()) {
                b.queryParam(nameValuePairs[i].toString(), value);
            }
        }
        return b.encode().build().toUriString();
    }

    static String pathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
