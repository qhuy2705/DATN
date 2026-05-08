package com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBranchRequest {

    @NotBlank(message = "nameVn là bắt buộc")
    @Size(max = 255, message = "nameVn tối đa 255 ký tự")
    private String nameVn;

    @NotBlank(message = "nameEn là bắt buộc")
    @Size(max = 255, message = "nameEn tối đa 255 ký tự")
    private String nameEn;

    @NotBlank(message = "addressVn là bắt buộc")
    @Size(max = 500, message = "addressVn tối đa 500 ký tự")
    private String addressVn;

    @NotBlank(message = "addressEn là bắt buộc")
    @Size(max = 500, message = "addressEn tối đa 500 ký tự")
    private String addressEn;

    private String phone;
    private String email;

    private Double latitude;
    private Double longitude;

    private String descriptionVn;
    private String descriptionEn;
}
