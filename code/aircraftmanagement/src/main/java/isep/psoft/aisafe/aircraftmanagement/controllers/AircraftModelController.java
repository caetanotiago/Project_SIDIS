package isep.psoft.aisafe.aircraftmanagement.controllers;

import isep.psoft.aisafe.aircraftmanagement.assemblers.AircraftAssembler;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.UpdateAircraftModelDTO;
import isep.psoft.aisafe.aircraftmanagement.services.CreateAircraftModelService;
import isep.psoft.aisafe.aircraftmanagement.services.SearchAircraftModelsService;
import isep.psoft.aisafe.aircraftmanagement.services.UpdateAircraftModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/aircraft-models")
@RequiredArgsConstructor
public class AircraftModelController {

    private final CreateAircraftModelService createService;
    private final SearchAircraftModelsService searchService;
    private final UpdateAircraftModelService updateService;
    private final AircraftAssembler assembler;

    /** US101 */
    @PostMapping
    public ResponseEntity<AircraftModelDTO> create(@Valid @RequestBody CreateAircraftModelDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toDTO(createService.create(dto)));
    }

    /** Ponte B1b */
    @GetMapping("/{modelName}")
    public AircraftModelDTO getByName(@PathVariable String modelName) {
        return assembler.toDTO(searchService.getByName(modelName));
    }

    /** US201 */
    @PatchMapping("/{modelName}")
    public AircraftModelDTO update(@PathVariable String modelName,
                                   @Valid @RequestBody UpdateAircraftModelDTO dto) {
        return updateService.update(modelName, dto);
    }
}
