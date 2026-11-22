package isep.psoft.aisafe.aircraftmanagement.controllers;

import isep.psoft.aisafe.aircraftmanagement.dto.AircraftMoodDTO;
import isep.psoft.aisafe.aircraftmanagement.query.AircraftMoodQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code GET /api/v2/unicorns} - the "mood" of every aircraft (SIDIS Assignment 2). The real implementation of the
 * contract that the Postman mock server stubs (docs/2-containers/Unicorns-Mock.md): a pure query served by the
 * projection {@code AIRCRAFT_MOOD}. There is no POST/PATCH here: the moods change only when an aircraft command
 * (US102, US105) produces an event.
 */
@RestController
@RequestMapping("/api/v2/unicorns")
@RequiredArgsConstructor
public class UnicornsController {

    private final AircraftMoodQueryService moods;

    @GetMapping
    public List<AircraftMoodDTO> moods() {
        return moods.moods();
    }
}
