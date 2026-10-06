package isep.psoft.aisafe.flightroutes.assemblers;

import isep.psoft.aisafe.flightroutes.domain.FlightRoute;
import isep.psoft.aisafe.flightroutes.domain.RouteHistory;
import isep.psoft.aisafe.flightroutes.domain.RouteSummary;
import isep.psoft.aisafe.flightroutes.domain.RouteUsage;
import isep.psoft.aisafe.flightroutes.dto.FlightRouteDTO;
import isep.psoft.aisafe.flightroutes.dto.RouteHistoryDTO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FlightRouteAssembler {

    public FlightRouteDTO toDTO(FlightRoute route) {
        return toDTO(route.toSummary());
    }

    public FlightRouteDTO toDTO(RouteSummary route) {
        FlightRouteDTO dto = new FlightRouteDTO();
        dto.setId(route.id());
        dto.setOriginIATA(route.originIata());
        dto.setDestIATA(route.destinationIata());
        dto.setDistance(route.distance());
        dto.setMinRange(route.minRange());
        dto.setMinCapacity(route.minCapacity());
        dto.setEstimatedFlightTime(route.estimatedFlightTime());
        dto.setStatus(route.status());
        return dto;
    }

    public List<FlightRouteDTO> toDTOList(List<RouteSummary> routes) {
        return routes.stream().map(this::toDTO).toList();
    }

    /** DTO recebido de uma réplica peer → vista de leitura. */
    public RouteSummary toSummary(FlightRouteDTO dto) {
        return new RouteSummary(dto.getId(), dto.getOriginIATA(), dto.getDestIATA(), dto.getDistance(),
                dto.getMinRange(), dto.getMinCapacity(), dto.getEstimatedFlightTime(), dto.getStatus());
    }

    // US214 - inclui a contagem de utilização (popularidade) no DTO
    public FlightRouteDTO toDTOWithUsage(RouteUsage routeUsage) {
        FlightRouteDTO dto = toDTO(routeUsage.route());
        dto.setUsageCount(routeUsage.usageCount());
        return dto;
    }

    public List<FlightRouteDTO> toDTOWithUsageList(List<RouteUsage> usages) {
        return usages.stream().map(this::toDTOWithUsage).toList();
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
        return historyLog.stream().map(this::toHistoryDTO).toList();
    }
}
