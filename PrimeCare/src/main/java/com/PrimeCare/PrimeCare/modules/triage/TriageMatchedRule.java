package com.PrimeCare.PrimeCare.modules.triage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageMatchedRule {
    private String id;
    private String priority;
    private String level;
    private String reason;
    private String source;

    @Builder.Default
    private List<String> matchedCodes = List.of();
}
