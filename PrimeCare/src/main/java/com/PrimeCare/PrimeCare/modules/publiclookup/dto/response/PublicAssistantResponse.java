package com.PrimeCare.PrimeCare.modules.publiclookup.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PublicAssistantResponse {
    private String answer;
    private String caution;
    private String provider;
    private List<PublicAssistantActionResponse> actions;
    private List<String> suggestions;
}
