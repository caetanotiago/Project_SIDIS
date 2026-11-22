package isep.psoft.aisafe.aircraftmanagement.assemblers;

import isep.psoft.aisafe.aircraftmanagement.domain.Aircraft;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModel;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftModelSummary;
import isep.psoft.aisafe.aircraftmanagement.domain.AircraftSummary;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftDTO;
import isep.psoft.aisafe.aircraftmanagement.dto.AircraftModelDTO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AircraftAssembler {

    // ---- Aircraft
    public AircraftDTO toDTO(Aircraft aircraft) {
        return toDTO(aircraft.toSummary());
    }

    public AircraftDTO toDTO(AircraftSummary s) {
        AircraftDTO dto = new AircraftDTO();
        dto.setRegistrationNumber(s.registrationNumber());
        dto.setModelName(s.modelName());
        dto.setSeatingCapacity(s.seatingCapacity());
        dto.setManufacturingDate(s.manufacturingDate());
        dto.setStatus(s.status().name());
        return dto;
    }

    public List<AircraftDTO> toDTOList(List<AircraftSummary> summaries) {
        return summaries.stream().map(this::toDTO).toList();
    }

    // ---- AircraftModel
    public AircraftModelDTO toDTO(AircraftModel model) {
        return toDTO(model.toSummary());
    }

    public AircraftModelDTO toDTO(AircraftModelSummary s) {
        AircraftModelDTO dto = new AircraftModelDTO();
        dto.setModelName(s.modelName());
        dto.setManufacturer(s.manufacturer());
        dto.setSeatingCapacity(s.seatingCapacity());
        dto.setFuelCapacity(s.fuelCapacity());
        dto.setMaximumRange(s.maximumRange());
        dto.setCruisingSpeed(s.cruisingSpeed());
        return dto;
    }
}
