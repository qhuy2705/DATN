package com.PrimeCare.PrimeCare.modules.service_result.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.PdfGenerationStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceOrderItemStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultTemplateCode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ServiceDeskQueueItemResponse {
    private Long itemId;
    private Long serviceOrderId;
    private String serviceOrderCode;
    private Long encounterId;
    private String encounterCode;
    private String appointmentCode;
    private String patientName;
    private String doctorName;
    private String branchName;
    private String departmentCode;
    private String serviceCode;
    private String serviceNameVn;
    private String serviceNameEn;
    private Integer queueNo;
    private ServiceOrderItemStatus itemStatus;
    private ServiceResultStatus resultStatus;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long resultId;
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
    private LocalDateTime performedAt;
    private String performedByName;
    private LocalDateTime verifiedAt;
    private String verifiedByName;
    private Integer turnaroundTargetMinutes;
    private LocalDateTime dueAt;
    private Long elapsedMinutes;
    private Long turnaroundMinutes;
    private Boolean overdue;
}
