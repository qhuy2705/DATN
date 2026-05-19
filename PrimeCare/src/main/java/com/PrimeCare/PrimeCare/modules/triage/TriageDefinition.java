package com.PrimeCare.PrimeCare.modules.triage;

import lombok.Data;

import java.util.List;

@Data
public class TriageDefinition {
    private String code;
    private String label;
    private List<String> synonyms = List.of();
    private Integer weight;
    private String priorityHint;
    private String riskGroup;
    private Boolean autoUrgent;
}
