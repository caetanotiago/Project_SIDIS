package isep.psoft.aisafe.aircraftmanagement.controllers;

import isep.psoft.aisafe.aircraftmanagement.dto.AircraftOperationalHoursDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.CompatibleRouteDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.PageDTO;
import isep.psoft.aisafe.aircraftmanagement.query.CalculateOperationalHoursService;
import isep.psoft.aisafe.aircraftmanagement.query.FindCompatibleRoutesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Aircraft queries that combine Flight Routes data (US203 and US206). Assignment 2: the Flight Routes data comes from
 * local read models fed by its events (no HTTP call to Flight Routes, never {@code X-Partial-Response}).
 */
@RestController
@RequestMapping("/api/aircrafts")
@RequiredArgsConstructor
public class AircraftStatisticsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CalculateOperationalHoursService operationalHoursService;
    private final FindCompatibleRoutesService compatibleRoutesService;

    /** US206 (query, also bridge B3d) - operational hours of every aircraft of the fleet (paginated). */
    @GetMapping("/operational-hours")
    public PageDTO<AircraftOperationalHoursDTO> operationalHours(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageDTO.of(operationalHoursService.calculate(), page, size);
    }

    /** US203 (query) - active routes compatible with the range of the model and the capacity of the aircraft. */
    @GetMapping("/{registrationNumber}/compatible-routes")
    public List<CompatibleRouteDTO> compatibleRoutes(@PathVariable String registrationNumber) {
        return compatibleRoutesService.findFor(registrationNumber);
    }
}
