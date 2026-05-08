package com.PrimeCare.PrimeCare.modules.masterdata.specialty.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateSpecialtyStatusRequest {

    @NotBlank
    private String status;
}
