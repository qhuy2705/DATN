package com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateBranchSpecialtyRequest {

    @NotNull
    private Long branchId;

    @NotNull
    private Long specialtyId;

    @Min(0)
    private Integer displayOrder;

    @Min(0)
    private Long consultationFee;

    @Min(1)
    private Integer slotMinutesOverride;

    private String note;
}