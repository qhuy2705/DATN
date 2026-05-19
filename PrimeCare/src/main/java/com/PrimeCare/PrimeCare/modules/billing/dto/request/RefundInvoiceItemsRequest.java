package com.PrimeCare.PrimeCare.modules.billing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RefundInvoiceItemsRequest {

    @NotBlank
    private String reason;

    @Valid
    @NotEmpty
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull
        private Long invoiceItemId;
    }
}
