package com.PrimeCare.PrimeCare.modules.service_result.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.ServiceResultTemplateCode;
import lombok.Data;

@Data
public class SubmitServiceResultRequest {
    private String resultTextVn;
    private String resultTextEn;
    private String resultDataJson;
    private String fieldValuesJson;
    private String attachmentUrl;
    private String attachmentMimeType;
    private String attachmentUrlsJson;
    private String conclusionText;
    private String impressionText;
    private ServiceResultTemplateCode templateCode;
    private String templateSchemaJson;
    private String reportTitle;
}
