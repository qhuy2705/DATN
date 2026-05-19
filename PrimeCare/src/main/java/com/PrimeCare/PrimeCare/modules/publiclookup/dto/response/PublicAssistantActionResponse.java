package com.PrimeCare.PrimeCare.modules.publiclookup.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PublicAssistantActionResponse {
    private String label;
    private String type;
    private String value;
    private Map<String, Object> payload;
}
