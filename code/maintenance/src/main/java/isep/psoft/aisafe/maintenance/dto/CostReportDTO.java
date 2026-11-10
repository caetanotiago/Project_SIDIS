package isep.psoft.aisafe.maintenance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CostReportDTO(String groupBy, BigDecimal totalCost, long records, List<CostItem> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CostItem(String key, BigDecimal totalCost, long records) {
    }
}
