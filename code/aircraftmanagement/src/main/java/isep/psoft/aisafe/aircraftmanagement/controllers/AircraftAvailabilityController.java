package isep.psoft.aisafe.aircraftmanagement.controllers;

import isep.psoft.aisafe.aircraftmanagement.dto.AircraftAvailabilityDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.FleetAvailabilityDTO;
import isep.psoft.aisafe.aircraftmanagement.services.AircraftAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aircrafts")
@RequiredArgsConstructor
public class AircraftAvailabilityController {

    private final AircraftAvailabilityService availabilityService;

    /** US205 – disponibilidade de uma aeronave. */
    @GetMapping("/{registrationNumber}/availability")
    public AircraftAvailabilityDTO availability(@PathVariable String registrationNumber) {
        return availabilityService.forAircraft(registrationNumber);
    }

    /** US205 – disponibilidade da frota (contagem por estado e taxa de disponibilidade). */
    @GetMapping("/availability")
    public FleetAvailabilityDTO fleetAvailability() {
        return availabilityService.forFleet();
    }
}
