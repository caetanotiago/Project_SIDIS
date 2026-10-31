package isep.psoft.aisafe.maintenance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/** US220: custos dos registos concluídos, por aeronave (AIRCRAFT) ou por modelo (MODEL). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CostReportDTO(String groupBy, BigDecimal totalCost, long records, List<CostItem> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CostItem(String key, BigDecimal totalCost, long records) {
    }
}
