package com.PrimeCare.PrimeCare.modules.pharmacy.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class MedicationBatchResponse {
    private Long id;
    private Long batchId;
    private Long medicationId;
    private String medicationName;
    private String unit;
    private String batchNumber;
    private String batchCode;
    private Integer quantityInStock;
    private Integer quantity;
    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    private String status;
    private Long importPrice;
    private Long sellPrice;
    private String supplier;
}
