package com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDoctorStatusRequest {

    @NotNull(message = "status là bắt buộc")
    private DoctorStatus status;
}