package com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateBranchSpecialtyRequest {

    @NotNull
    private BranchSpecialtyStatus status;

    @Min(0)
    private Integer displayOrder;

    @Min(0)
    private Long consultationFee;

    @Min(1)
    private Integer slotMinutesOverride;

    private String note;
}