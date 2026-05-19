package com.PrimeCare.PrimeCare.modules.triage;

import lombok.Data;

import java.util.List;

@Data
public class TriageRuleDefinition {
    private String id;
    private List<String> ifAll = List.of();
    private List<String> ifAny = List.of();
    private String priority;
    private String level;
    private String reason;
}
