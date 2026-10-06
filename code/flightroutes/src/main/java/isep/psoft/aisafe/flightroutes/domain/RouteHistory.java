package isep.psoft.aisafe.flightroutes.domain;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entrada (imutável) do histórico de alterações de uma rota (US111).
 * Regista sempre {@code changeDate} + {@code description} e, dinamicamente, apenas os valores
 * ANTERIORES ({@code previous*}) e NOVOS ({@code new*}) dos atributos efetivamente alterados
 * (os restantes ficam a {@code null} e são omitidos do JSON pelo {@code @JsonInclude(NON_NULL)} do DTO).
 * Os valores "novos" são capturados no momento da alteração (a rota é mutável; ler em runtime daria
 * sempre o estado final, não o estado de cada entrada).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteHistory {

    private LocalDateTime changeDate;
    private String description;

    // Valores anteriores — apenas preenchidos para os atributos realmente alterados.
    private Double previousMinRange;
    private Integer previousMinCapacity;
    private Integer previousEstimatedFlightTime;
    private String previousStatus;

    // Valores novos (para que o atributo mudou) — apenas preenchidos para os atributos realmente alterados.
    private Double newMinRange;
    private Integer newMinCapacity;
    private Integer newEstimatedFlightTime;
    private String newStatus;

    private RouteHistory(String description,
                         Double previousMinRange, Integer previousMinCapacity,
                         Integer previousEstimatedFlightTime, String previousStatus,
                         Double newMinRange, Integer newMinCapacity,
                         Integer newEstimatedFlightTime, String newStatus) {
        this.changeDate = LocalDateTime.now();
        this.description = description;
        this.previousMinRange = previousMinRange;
        this.previousMinCapacity = previousMinCapacity;
        this.previousEstimatedFlightTime = previousEstimatedFlightTime;
        this.previousStatus = previousStatus;
        this.newMinRange = newMinRange;
        this.newMinCapacity = newMinCapacity;
        this.newEstimatedFlightTime = newEstimatedFlightTime;
        this.newStatus = newStatus;
    }

    /** Registo da criação da rota — guarda os valores iniciais como "novos". */
    public static RouteHistory created(Double newMinRange, Integer newMinCapacity,
                                       Integer newEstimatedFlightTime, String newStatus) {
        return new RouteHistory("Route created.",
                null, null, null, null,
                newMinRange, newMinCapacity, newEstimatedFlightTime, newStatus);
    }

    /**
     * Registo de atualização de detalhes operacionais. Cada par {@code previous}/{@code new} só deve vir
     * preenchido quando o respetivo atributo foi realmente alterado (caso contrário ambos {@code null}).
     */
    public static RouteHistory detailsUpdated(Double previousMinRange, Integer previousMinCapacity,
                                              Integer previousEstimatedFlightTime,
                                              Double newMinRange, Integer newMinCapacity,
                                              Integer newEstimatedFlightTime) {
        return new RouteHistory("Route details updated.",
                previousMinRange, previousMinCapacity, previousEstimatedFlightTime, null,
                newMinRange, newMinCapacity, newEstimatedFlightTime, null);
    }

    /** Registo de mudança de estado (ativação/desativação) — guarda o estado anterior e o novo. */
    public static RouteHistory statusChanged(String previousStatus, String newStatus, String description) {
        return new RouteHistory(description,
                null, null, null, previousStatus,
                null, null, null, newStatus);
    }
}
