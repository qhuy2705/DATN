package com.PrimeCare.PrimeCare.modules.staff.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStaffProfileRequest {
    @NotBlank
    private String fullName;

    @NotNull
    private Long branchId;
}
