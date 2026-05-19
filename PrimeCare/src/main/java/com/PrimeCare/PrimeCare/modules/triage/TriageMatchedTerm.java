package com.PrimeCare.PrimeCare.modules.triage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageMatchedTerm {
    private String term;
    private String code;
    private String label;
    private String category;
    private String source;
    private String evidenceText;
    private Integer weight;
}
