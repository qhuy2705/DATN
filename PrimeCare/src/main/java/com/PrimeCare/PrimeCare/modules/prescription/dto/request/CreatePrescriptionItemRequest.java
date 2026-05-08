package com.PrimeCare.PrimeCare.modules.prescription.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePrescriptionItemRequest {

    @NotNull
    private Long medicationId;

    @NotNull
    @Min(1)
    private Integer quantity;

    @Size(max = 128)
    private String dose;

    @Size(max = 128)
    private String frequency;

    @Min(1)
    private Integer durationDays;

    @Size(max = 128)
    private String route;

    @Size(max = 2000)
    private String instruction;
}