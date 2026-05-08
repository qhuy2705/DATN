package com.PrimeCare.PrimeCare.modules.pharmacy.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateMedicationBatchRequest {
    private String batchNumber;

    @Min(value = 0, message = "Số lượng không được âm")
    private Integer quantity;

    private LocalDate manufactureDate;
    private LocalDate expiryDate;

    @Min(value = 0, message = "Giá nhập không được âm")
    private Long importPrice;

    @Min(value = 0, message = "Giá bán không được âm")
    private Long sellPrice;

    private String supplier;
}
