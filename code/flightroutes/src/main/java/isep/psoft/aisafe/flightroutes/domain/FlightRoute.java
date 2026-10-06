package isep.psoft.aisafe.flightroutes.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) 
public class FlightRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id; // O Route ID gerado pelo sistema 

    // Referência ao agregado Airport (microsserviço Airports) por identidade: o código IATA.
    @Column(name = "origin_iata", nullable = false, length = 3)
    private String originIata;

    @Column(name = "destination_iata", nullable = false, length = 3)
    private String destinationIata;

    @Embedded
    private RouteDistance distance;

    @Embedded
    private RouteRequirements requirements;

    @Embedded
    private EstimatedFlightTime estimatedFlightTime;

    @Embedded
    private RouteStatus status;

    @Version
    private Long version; // Optimistic Locking (Impede conflitos de concorrência)

    @ElementCollection
    @CollectionTable(name = "ROUTE_HISTORY_LOG", joinColumns = @JoinColumn(name = "ROUTE_ID"))
    private List<RouteHistory> historyLog = new ArrayList<>();

    // CORREÇÃO: O construtor precisa de ser público para ser chamado pela Factory, que está noutro pacote.
    public FlightRoute(String originIata, String destinationIata, RouteDistance distance,
                          RouteRequirements requirements, EstimatedFlightTime estimatedFlightTime) {
        if (originIata == null || originIata.isBlank() || destinationIata == null || destinationIata.isBlank()) {
            throw new IllegalArgumentException("Origin and destination airports are required.");
        }
        this.originIata = originIata.toUpperCase();
        this.destinationIata = destinationIata.toUpperCase();
        this.distance = distance;
        this.requirements = requirements;
        this.estimatedFlightTime = estimatedFlightTime;
        this.status = RouteStatus.active(); // Uma rota nasce sempre ativa

        // Regista a criação no histórico (US111) com os valores iniciais como "novos".
        this.historyLog.add(RouteHistory.created(
                requirements.getMinRange(), requirements.getMinCapacity(),
                estimatedFlightTime.getDurationMinutes(), this.status.getState()));
    }

    // US112 (Update) — atualização parcial dos detalhes operacionais.
    // Cada parâmetro a null = atributo não alterado. Regista no histórico apenas os valores
    // anteriores dos atributos efetivamente alterados (US111 — histórico dinâmico).
    public void updateDetails(Double newMinRange, Integer newMinCapacity, Integer newTime) {
        Double effMinRange = this.requirements.getMinRange();
        Integer effMinCapacity = this.requirements.getMinCapacity();
        Integer effTime = this.estimatedFlightTime.getDurationMinutes();

        Double prevMinRange = null;
        Integer prevMinCapacity = null;
        Integer prevTime = null;

        // Valores novos — só preenchidos para os atributos efetivamente alterados (senão null).
        Double changedMinRange = null;
        Integer changedMinCapacity = null;
        Integer changedTime = null;

        if (newMinRange != null && !newMinRange.equals(effMinRange)) {
            prevMinRange = effMinRange;
            changedMinRange = newMinRange;
            effMinRange = newMinRange;
        }
        if (newMinCapacity != null && !newMinCapacity.equals(effMinCapacity)) {
            prevMinCapacity = effMinCapacity;
            changedMinCapacity = newMinCapacity;
            effMinCapacity = newMinCapacity;
        }
        if (newTime != null && !newTime.equals(effTime)) {
            prevTime = effTime;
            changedTime = newTime;
            effTime = newTime;
        }

        // Nada mudou de facto → não altera estado nem regista histórico.
        if (prevMinRange == null && prevMinCapacity == null && prevTime == null) {
            return;
        }

        this.requirements = new RouteRequirements(effMinRange, effMinCapacity);
        this.estimatedFlightTime = new EstimatedFlightTime(effTime);
        this.historyLog.add(RouteHistory.detailsUpdated(
                prevMinRange, prevMinCapacity, prevTime,
                changedMinRange, changedMinCapacity, changedTime));
    }

    /** Vista de leitura independente da réplica (usada nas consultas agregadas entre réplicas). */
    public RouteSummary toSummary() {
        return new RouteSummary(id, originIata, destinationIata, distance.getDistance(),
                requirements.getMinRange(), requirements.getMinCapacity(),
                estimatedFlightTime.getDurationMinutes(), status.getState());
    }

    // US112 (Activate/Deactivate) — "deactivate a route" = mudar o status para INACTIVE.
    public void changeStatus(String newState) {
        String current = this.status.getState();

        if ("INACTIVE".equalsIgnoreCase(newState)) {
            if ("INACTIVE".equalsIgnoreCase(current)) {
                throw new IllegalStateException("Route is already INACTIVE.");
            }
            this.status = RouteStatus.inactive();
            this.historyLog.add(RouteHistory.statusChanged(current, "INACTIVE", "Route deactivated."));
        } else if ("ACTIVE".equalsIgnoreCase(newState)) {
            if ("ACTIVE".equalsIgnoreCase(current)) {
                throw new IllegalStateException("Route is already ACTIVE.");
            }
            this.status = RouteStatus.active();
            this.historyLog.add(RouteHistory.statusChanged(current, "ACTIVE", "Route activated."));
        } else {
            throw new IllegalArgumentException("Invalid status: " + newState + " (must be ACTIVE or INACTIVE).");
        }
    }
}
