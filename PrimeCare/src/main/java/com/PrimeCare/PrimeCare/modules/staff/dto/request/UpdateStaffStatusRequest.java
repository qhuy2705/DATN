package com.PrimeCare.PrimeCare.modules.staff.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.StaffStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStaffStatusRequest {
    @NotNull
    private StaffStatus status;
}