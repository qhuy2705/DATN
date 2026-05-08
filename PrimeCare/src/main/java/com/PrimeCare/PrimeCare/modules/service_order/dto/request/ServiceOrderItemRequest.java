package com.PrimeCare.PrimeCare.modules.service_order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ServiceOrderItemRequest {
    @NotNull
    private Long medicalServiceId;
    @Min(1)
    private Integer quantity = 1;
    private String note;
}
