package isep.psoft.aisafe.flightroutes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Value Object: referência de um ScheduledFlight à rota que opera, por identidade (routeId), mais
 * uma cópia dos dados da rota no momento do agendamento (origem, destino, duração).
 *
 * <p>Com réplicas, a rota pode estar na base de dados de OUTRA réplica, por isso não pode haver
 * chave estrangeira/JOIN para FlightRoute. A cópia permite responder a US206 (horas operacionais)
 * e US213 sem consultar a rota, e preserva o voo tal como foi agendado mesmo que a rota mude depois.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Exigido pelo JPA
public class RouteReference {

    @Column(name = "route_id", nullable = false)
    private String routeId;

    @Column(name = "route_origin_iata", nullable = false, length = 3)
    private String originIata;

    @Column(name = "route_destination_iata", nullable = false, length = 3)
    private String destinationIata;

    @Column(name = "route_flight_minutes", nullable = false)
    private Integer estimatedFlightTimeMinutes;

    public RouteReference(String routeId, String originIata, String destinationIata,
                          Integer estimatedFlightTimeMinutes) {
        if (routeId == null || routeId.isBlank()) {
            throw new IllegalArgumentException("Route id is required.");
        }
        if (originIata == null || originIata.isBlank() || destinationIata == null || destinationIata.isBlank()) {
            throw new IllegalArgumentException("Route origin and destination are required.");
        }
        if (estimatedFlightTimeMinutes == null || estimatedFlightTimeMinutes <= 0) {
            throw new IllegalArgumentException("Flight time must be positive.");
        }
        this.routeId = routeId;
        this.originIata = originIata.toUpperCase();
        this.destinationIata = destinationIata.toUpperCase();
        this.estimatedFlightTimeMinutes = estimatedFlightTimeMinutes;
    }

    public static RouteReference of(RouteSummary route) {
        return new RouteReference(route.id(), route.originIata(), route.destinationIata(),
                route.estimatedFlightTime());
    }
}
