package com.PrimeCare.PrimeCare.modules.medical_service.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicMedicalServiceCatalogResponse {
    private Long id;
    private String code;
    private String nameVn;
    private String nameEn;
    private String descriptionVn;
    private String descriptionEn;
    private MedicalServiceType serviceType;
    private Long basePrice;
    private Integer displayOrder;
    private String thumbnailUrl;
    private Integer defaultTurnaroundMinutes;
}
