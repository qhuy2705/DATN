package com.PrimeCare.PrimeCare.modules.publiclookup.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResultLookupSummaryResponse {
    private String appointmentCode;
    private String encounterCode;
    private String patientFullName;
    private String doctorName;
    private String specialtyName;
    private String branchName;
    private String visitDate;
    private String encounterStatus;
    private String finalDiagnosis;
    private String conclusion;
    private Integer serviceResultCount;
    private Integer completedResultCount;
    private Integer verifiedResultCount;
    private Boolean doctorConcluded;
    private Boolean pdfReady;
}
