package com.PrimeCare.PrimeCare.modules.medical_service.entity;

import com.PrimeCare.PrimeCare.shared.auditing.BaseEntity;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceStatus;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceType;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultTemplateCode;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "medical_services")
public class MedicalService extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;
    @Column(name = "name_vn", nullable = false, length = 255)
    private String nameVn;
    @Column(name = "name_en", length = 255)
    private String nameEn;
    @Column(name = "description_vn", columnDefinition = "TEXT")
    private String descriptionVn;
    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;
    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 32)
    private MedicalServiceType serviceType;
    @Column(name = "department_code", length = 64)
    private String departmentCode;
    @Column(name = "base_price", nullable = false)
    private Long basePrice;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MedicalServiceStatus status;
    @Column(name = "public_visible", nullable = false)
    private Boolean publicVisible;
    @Column(name = "display_order")
    private Integer displayOrder;
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;
    @Column(name = "default_turnaround_minutes")
    private Integer defaultTurnaroundMinutes;
    @Column(name = "requires_file_result", nullable = false)
    private Boolean requiresFileResult;
    @Column(name = "requires_numeric_result", nullable = false)
    private Boolean requiresNumericResult;
    @Enumerated(EnumType.STRING)
    @Column(name = "result_template_code", length = 32)
    private ServiceResultTemplateCode resultTemplateCode;
    @Column(name = "result_template_schema_json", columnDefinition = "TEXT")
    private String resultTemplateSchemaJson;
    @Column(name = "result_report_title", length = 255)
    private String resultReportTitle;

    @PrePersist
    void prePersist() {
        if (status == null) status = MedicalServiceStatus.ACTIVE;
        if (publicVisible == null) publicVisible = false;
        if (displayOrder == null) displayOrder = 0;
        if (requiresFileResult == null) requiresFileResult = false;
        if (requiresNumericResult == null) requiresNumericResult = false;
        if (resultTemplateCode == null) resultTemplateCode = ServiceResultTemplateCode.GENERIC_NARRATIVE;
    }
}
