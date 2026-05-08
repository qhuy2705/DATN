package com.PrimeCare.PrimeCare.modules.service_result.support;

import com.PrimeCare.PrimeCare.modules.medical_service.entity.MedicalService;
import com.PrimeCare.PrimeCare.shared.enums.MedicalServiceType;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultTemplateCode;

public final class ServiceResultTemplateSupport {

    private ServiceResultTemplateSupport() {
    }

    public static ServiceResultTemplateCode resolveTemplateCode(
            ServiceResultTemplateCode explicitTemplateCode,
            MedicalServiceType serviceType,
            Boolean requiresNumericResult,
            Boolean requiresFileResult
    ) {
        if (explicitTemplateCode != null) {
            return explicitTemplateCode;
        }
        if (Boolean.TRUE.equals(requiresNumericResult) || serviceType == MedicalServiceType.LAB) {
            return ServiceResultTemplateCode.LAB_TABLE;
        }
        if (serviceType == MedicalServiceType.IMAGING || serviceType == MedicalServiceType.FUNCTIONAL_TEST || Boolean.TRUE.equals(requiresFileResult)) {
            return ServiceResultTemplateCode.IMAGING_REPORT;
        }
        if (serviceType == MedicalServiceType.PROCEDURE) {
            return ServiceResultTemplateCode.PROCEDURE_REPORT;
        }
        return ServiceResultTemplateCode.GENERIC_NARRATIVE;
    }

    public static ServiceResultTemplateCode resolveTemplateCode(MedicalService medicalService) {
        if (medicalService == null) {
            return ServiceResultTemplateCode.GENERIC_NARRATIVE;
        }
        return resolveTemplateCode(
                medicalService.getResultTemplateCode(),
                medicalService.getServiceType(),
                medicalService.getRequiresNumericResult(),
                medicalService.getRequiresFileResult()
        );
    }

    public static String resolveReportTitle(String explicitTitle, String fallbackName) {
        if (explicitTitle != null && !explicitTitle.isBlank()) {
            return explicitTitle.trim();
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            return fallbackName.trim();
        }
        return "Phiếu kết quả dịch vụ";
    }

    public static String resolveReportTitle(MedicalService medicalService) {
        if (medicalService == null) {
            return "Phiếu kết quả dịch vụ";
        }
        return resolveReportTitle(medicalService.getResultReportTitle(), medicalService.getNameVn());
    }

    public static String defaultSchemaJson(ServiceResultTemplateCode templateCode) {
        ServiceResultTemplateCode safeTemplate = templateCode != null
                ? templateCode
                : ServiceResultTemplateCode.GENERIC_NARRATIVE;

        return switch (safeTemplate) {
            case LAB_TABLE -> """
                    {
                      "template": "LAB_TABLE",
                      "rowsKey": "rows",
                      "meta": [
                        {"key": "specimen", "label": "Mẫu bệnh phẩm"},
                        {"key": "clinicalInfo", "label": "Chỉ định lâm sàng"},
                        {"key": "deviceName", "label": "Thiết bị / máy"}
                      ],
                      "columns": [
                        {"key": "parameter", "label": "Thông số"},
                        {"key": "result", "label": "Kết quả"},
                        {"key": "unit", "label": "Đơn vị"},
                        {"key": "referenceRange", "label": "Khoảng tham chiếu"},
                        {"key": "flag", "label": "Đánh dấu"},
                        {"key": "note", "label": "Ghi chú"}
                      ]
                    }
                    """;
            case IMAGING_REPORT -> """
                    {
                      "template": "IMAGING_REPORT",
                      "sections": [
                        {"key": "clinicalInfo", "label": "Thông tin lâm sàng"},
                        {"key": "technique", "label": "Kỹ thuật thực hiện"},
                        {"key": "findings", "label": "Mô tả / Findings"},
                        {"key": "impression", "label": "Kết luận / Impression"},
                        {"key": "recommendation", "label": "Khuyến nghị"}
                      ]
                    }
                    """;
            case PROCEDURE_REPORT -> """
                    {
                      "template": "PROCEDURE_REPORT",
                      "sections": [
                        {"key": "procedureName", "label": "Thủ thuật"},
                        {"key": "specimen", "label": "Bệnh phẩm"},
                        {"key": "macroscopy", "label": "Đại thể"},
                        {"key": "microscopy", "label": "Vi thể"},
                        {"key": "conclusion", "label": "Kết luận"}
                      ]
                    }
                    """;
            case GENERIC_NARRATIVE -> """
                    {
                      "template": "GENERIC_NARRATIVE",
                      "sections": [
                        {"key": "summary", "label": "Tóm tắt kết quả"},
                        {"key": "conclusion", "label": "Kết luận"},
                        {"key": "recommendation", "label": "Khuyến nghị"}
                      ]
                    }
                    """;
        };
    }
}
