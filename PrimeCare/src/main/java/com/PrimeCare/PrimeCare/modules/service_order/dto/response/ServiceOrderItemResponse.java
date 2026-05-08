package com.PrimeCare.PrimeCare.modules.service_order.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.PdfGenerationStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultTemplateCode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ServiceOrderItemResponse {
    private Long id;
    private Long medicalServiceId;
    private String serviceCode;
    private String serviceNameVn;
    private String serviceNameEn;
    private Long price;
    private Integer quantity;
    private Long lineTotalAmount;
    private String departmentCode;
    private Integer queueNo;
    private ServiceOrderItemStatus status;
    private ServiceResultStatus resultStatus;
    private String resultTextVn;
    private String resultTextEn;
    private String resultDataJson;
    private String fieldValuesJson;
    private String attachmentUrl;
    private String attachmentMimeType;
    private String attachmentUrlsJson;
    private ServiceResultTemplateCode templateCode;
    private String templateSchemaJson;
    private String conclusionText;
    private String impressionText;
    private String reportTitle;
    private String reportPdfUrl;
    private PdfGenerationStatus reportPdfStatus;
    private String reportPdfErrorMessage;
    private LocalDateTime reportPdfGeneratedAt;
    private LocalDateTime resultPerformedAt;
}
