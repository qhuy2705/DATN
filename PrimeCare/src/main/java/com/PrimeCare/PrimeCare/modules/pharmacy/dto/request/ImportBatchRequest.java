package com.PrimeCare.PrimeCare.modules.pharmacy.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ImportBatchRequest {
    @NotNull(message = "Mã thuốc không được để trống")
    private Long medicationId;

    @NotBlank(message = "Số lô không được để trống")
    private String batchNumber;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 0, message = "Số lượng không được âm")
    private Integer quantity;

    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    private Long importPrice;
    private Long sellPrice;
    private String supplier;
}
