package isep.psoft.aisafe.aircraftmanagement.controllers;

import isep.psoft.aisafe.aircraftmanagement.assemblers.AircraftAssembler;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.CreateAircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.PageDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.UpdateAircraftStatusDTO;
import isep.psoft.aisafe.aircraftmanagement.services.CreateAircraftService;
import isep.psoft.aisafe.aircraftmanagement.services.SearchAircraftsService;
import isep.psoft.aisafe.aircraftmanagement.services.UpdateAircraftStatusService;
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

    /** US102 */
    @PostMapping
    public ResponseEntity<AircraftDTO> create(@Valid @RequestBody CreateAircraftDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toDTO(createService.create(dto)));
    }

    /**
     * US104 – pesquisa por modelo, estado ou ano de fabrico, agregada entre réplicas.
     * Cliente: devolve uma página (PageDTO). Pedido de um peer (X-Peer-Hops): devolve a lista local
     * completa e sem paginar, que a réplica que recebeu o pedido junta e pagina.
     */
    @GetMapping
    public Object search(@RequestParam(required = false) String modelName,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) Integer manufacturingYear,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        List<AircraftSummary> result = searchService.search(modelName, status, manufacturingYear);
        List<AircraftDTO> dtos = assembler.toDTOList(result);
        return searchService.isPeerRequest() ? dtos : PageDTO.of(dtos, page, size);
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
