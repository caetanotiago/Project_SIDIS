package isep.psoft.aisafe.flightroutes.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Histórico dinâmico (US111): além de {@code changeDate}/{@code description}, só são serializados
 * os valores anteriores ({@code previous*}) e novos ({@code new*}) dos atributos efetivamente
 * alterados (os {@code null} são omitidos).
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true) // tolera campos extra nas respostas de outras réplicas
public class RouteHistoryDTO {
    private String changeDate;
    private String description;
    private Double previousMinRange;
    private Integer previousMinCapacity;
    private Integer previousEstimatedFlightTime;
    private String previousStatus;
    private Double newMinRange;
    private Integer newMinCapacity;
    private Integer newEstimatedFlightTime;
    private String newStatus;
}
