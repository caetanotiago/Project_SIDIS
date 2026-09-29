package isep.psoft.aisafe.flightroutes.assemblers;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.domain.RouteHistory;
import isep.psoft.aisafe.flightroutes.domain.RouteUsage;
import isep.psoft.aisafe.flightroutes.dto.FlightRouteDTO;
import isep.psoft.aisafe.flightroutes.dto.RouteHistoryDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class FlightRouteAssembler {

    public FlightRouteDTO toDTO(FlightRoute route) {
        FlightRouteDTO dto = new FlightRouteDTO();
        dto.setId(route.getId());
        
        dto.setOriginIATA(route.getOriginIata());
        dto.setDestIATA(route.getDestinationIata());
        
        dto.setDistance(route.getDistance().getDistance());
        dto.setMinRange(route.getRequirements().getMinRange());
        dto.setMinCapacity(route.getRequirements().getMinCapacity());
        dto.setEstimatedFlightTime(route.getEstimatedFlightTime().getDurationMinutes());
        dto.setStatus(route.getStatus().getState());
        
        return dto;
    }

    public List<FlightRouteDTO> toDTOList(List<FlightRoute> routes) {
        return routes.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // US214 - inclui a contagem de utilização (popularidade) no DTO
    public FlightRouteDTO toDTOWithUsage(RouteUsage routeUsage) {
        FlightRouteDTO dto = toDTO(routeUsage.getRoute());
        dto.setUsageCount(routeUsage.getUsageCount());
        return dto;
    }

    public List<FlightRouteDTO> toDTOWithUsageList(List<RouteUsage> usages) {
        return usages.stream().map(this::toDTOWithUsage).collect(Collectors.toList());
    }

    public RouteHistoryDTO toHistoryDTO(RouteHistory history) {
        RouteHistoryDTO dto = new RouteHistoryDTO();
        dto.setChangeDate(history.getChangeDate().toString());
        dto.setDescription(history.getDescription());
        dto.setPreviousMinRange(history.getPreviousMinRange());
        dto.setPreviousMinCapacity(history.getPreviousMinCapacity());
        dto.setPreviousEstimatedFlightTime(history.getPreviousEstimatedFlightTime());
        dto.setPreviousStatus(history.getPreviousStatus());
        dto.setNewMinRange(history.getNewMinRange());
        dto.setNewMinCapacity(history.getNewMinCapacity());
        dto.setNewEstimatedFlightTime(history.getNewEstimatedFlightTime());
        dto.setNewStatus(history.getNewStatus());
        return dto;
    }

    public List<RouteHistoryDTO> toHistoryDTOList(List<RouteHistory> historyLog) {
        return historyLog.stream().map(this::toHistoryDTO).collect(Collectors.toList());
    }
}