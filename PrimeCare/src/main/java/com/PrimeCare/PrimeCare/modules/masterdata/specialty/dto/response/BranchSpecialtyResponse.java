package com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.BranchSpecialtyStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BranchSpecialtyResponse {
    private Long id;

    private Long branchId;
    private String branchName;

    private Long specialtyId;
    private String specialtyCode;
    private String specialtyNameVn;
    private String specialtyNameEn;
    private String specialtyDescriptionVn;
    private String specialtyDescriptionEn;
    private String iconUrl;

    private BranchSpecialtyStatus status;
    private String effectiveStatus;
    private String inactiveReason;
    private boolean bookable;
    private Integer displayOrder;
    private Long consultationFee;
    private Integer slotMinutesOverride;
    private String note;
}