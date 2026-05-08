package com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBranchStatusRequest {
    @NotNull(message = "status là bắt buộc")
    private BranchStatus status;
}
