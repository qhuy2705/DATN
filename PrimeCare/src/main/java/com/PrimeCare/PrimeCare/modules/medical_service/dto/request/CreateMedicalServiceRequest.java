package com.PrimeCare.PrimeCare.modules.medical_service.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceType;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultTemplateCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMedicalServiceRequest {
    @NotBlank private String code;
    @NotBlank private String nameVn;
    private String nameEn;
    private String descriptionVn;
    private String descriptionEn;
    @NotNull private MedicalServiceType serviceType;
    private String departmentCode;
    @NotNull private Long basePrice;
    private Boolean publicVisible;
    private Integer displayOrder;
    private String thumbnailUrl;
    private Integer defaultTurnaroundMinutes;
    private Boolean requiresFileResult;
    private Boolean requiresNumericResult;
    private ServiceResultTemplateCode resultTemplateCode;
    private String resultTemplateSchemaJson;
    private String resultReportTitle;
}
