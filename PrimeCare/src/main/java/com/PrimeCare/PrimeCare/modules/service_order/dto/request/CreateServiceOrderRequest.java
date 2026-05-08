package com.PrimeCare.PrimeCare.modules.service_order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateServiceOrderRequest {
    @Valid
    @NotEmpty
    private List<ServiceOrderItemRequest> items;
    private String note;
}
