package isep.psoft.aisafe.aircraftmanagement.controllers;

import isep.psoft.aisafe.aircraftmanagement.assemblers.AircraftAssembler;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.PageDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.UpdateAircraftStatusDTO;
import isep.psoft.aisafe.aircraftmanagement.command.CreateAircraftService;
import isep.psoft.aisafe.aircraftmanagement.command.UpdateAircraftStatusService;
import isep.psoft.aisafe.aircraftmanagement.query.SearchAircraftsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/aircrafts")
@RequiredArgsConstructor
public class AircraftController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CreateAircraftService createService;
    private final SearchAircraftsService searchService;
    private final UpdateAircraftStatusService updateStatusService;
    private final AircraftAssembler assembler;

    /** US102 (command) */
    @PostMapping
    public ResponseEntity<AircraftDTO> create(@Valid @RequestBody CreateAircraftDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toDTO(createService.create(dto)));
    }

    /**
     * US104 (query) - search by model, status or manufacturing year. Answered from this replica's read model, which
     * holds the aircraft of every replica (no peer aggregation any more); paginated in memory (PageDTO).
     */
    @GetMapping
    public PageDTO<AircraftDTO> search(@RequestParam(required = false) String modelName,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) Integer manufacturingYear,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        List<AircraftSummary> result = searchService.search(modelName, status, manufacturingYear);
        return PageDTO.of(assembler.toDTOList(result), page, size);
    }

    /** US103 / bridges B1a, B3a (query) */
    @GetMapping("/{registrationNumber}")
    public AircraftDTO getByRegistration(@PathVariable String registrationNumber) {
        return assembler.toDTO(searchService.getByRegistration(registrationNumber));
    }

    /** US105 / bridge B3b (command, kept synchronous: Integration-Contracts section 9.6) */
    @PatchMapping("/{registrationNumber}/status")
    public AircraftDTO updateStatus(@PathVariable String registrationNumber,
                                    @Valid @RequestBody UpdateAircraftStatusDTO dto) {
        return updateStatusService.updateStatus(registrationNumber, dto);
    }
}
