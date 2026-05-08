package com.PrimeCare.PrimeCare.modules.pharmacy.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockSummaryResponse {
    private Long medicationId;
    private String medicationName;
    private String unit;
    private Integer totalQuantity;
    private Integer totalStock;
    private Boolean lowStock;
    private Integer lowStockThreshold;
}
