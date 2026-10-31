package isep.psoft.aisafe.aircraftmanagement.controllers;

import isep.psoft.aisafe.aircraftmanagement.assemblers.AircraftAssembler;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.UpdateAircraftStatusDTO;
import isep.psoft.aisafe.aircraftmanagement.services.CreateAircraftService;
import isep.psoft.aisafe.aircraftmanagement.services.SearchAircraftsService;
import isep.psoft.aisafe.aircraftmanagement.services.UpdateAircraftStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/aircrafts")
@RequiredArgsConstructor
public class AircraftController {

    private final CreateAircraftService createService;
    private final SearchAircraftsService searchService;
    private final UpdateAircraftStatusService updateStatusService;
    private final AircraftAssembler assembler;

    /** US102 */
    @PostMapping
    public ResponseEntity<AircraftDTO> create(@Valid @RequestBody CreateAircraftDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toDTO(createService.create(dto)));
    }

    /** US103 / ponte B1a */
    @GetMapping("/{registrationNumber}")
    public AircraftDTO getByRegistration(@PathVariable String registrationNumber) {
        return assembler.toDTO(searchService.getByRegistration(registrationNumber));
    }

    /** US105 / ponte B3b */
    @PatchMapping("/{registrationNumber}/status")
    public AircraftDTO updateStatus(@PathVariable String registrationNumber,
                                    @Valid @RequestBody UpdateAircraftStatusDTO dto) {
        return updateStatusService.updateStatus(registrationNumber, dto);
    }
}
