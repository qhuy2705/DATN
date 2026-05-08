package com.PrimeCare.PrimeCare.modules.publiccontact.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePublicContactStatusRequest {
    @NotBlank(message = "Trạng thái không được để trống")
    @Size(max = 32, message = "Trạng thái tối đa 32 ký tự")
    private String status;
}
