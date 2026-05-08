package com.PrimeCare.PrimeCare.modules.publiclookup.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicAssistantActionResponse {
    private String label;
    private String type;
    private String value;
}
