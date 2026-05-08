package com.PrimeCare.PrimeCare.modules.medical_service.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceStatus;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceType;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultTemplateCode;
import lombok.Data;

@Data
public class UpdateMedicalServiceRequest {
    private String nameVn; private String nameEn; private String descriptionVn; private String descriptionEn; private MedicalServiceType serviceType; private String departmentCode; private Long basePrice; private MedicalServiceStatus status; private Boolean publicVisible; private Integer displayOrder; private String thumbnailUrl; private Integer defaultTurnaroundMinutes; private Boolean requiresFileResult; private Boolean requiresNumericResult; private ServiceResultTemplateCode resultTemplateCode; private String resultTemplateSchemaJson; private String resultReportTitle;
}
