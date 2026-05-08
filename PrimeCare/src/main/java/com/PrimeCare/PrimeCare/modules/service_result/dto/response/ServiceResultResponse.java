package com.PrimeCare.PrimeCare.modules.service_result.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.PdfGenerationStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultTemplateCode;
import lombok.Builder; import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class ServiceResultResponse {
    private Long id; private Long serviceOrderItemId; private String resultTextVn; private String resultTextEn; private String resultDataJson; private String fieldValuesJson; private String attachmentUrl; private String attachmentMimeType; private String attachmentUrlsJson; private ServiceResultTemplateCode templateCode; private String templateSchemaJson; private String conclusionText; private String impressionText; private String reportTitle; private String reportPdfUrl; private PdfGenerationStatus reportPdfStatus; private LocalDateTime reportPdfGeneratedAt; private String reportPdfErrorMessage; private ServiceResultStatus status; private Integer turnaroundTargetMinutes; private LocalDateTime dueAt; private Long elapsedMinutes; private Long turnaroundMinutes; private Boolean overdue;
}
