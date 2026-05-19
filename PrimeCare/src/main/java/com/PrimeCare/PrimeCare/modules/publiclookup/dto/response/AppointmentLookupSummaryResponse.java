package com.PrimeCare.PrimeCare.modules.publiclookup.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppointmentLookupSummaryResponse {
    private String code;
    private String status;
    private String patientFullName;
    private String doctorName;
    private String specialtyName;
    private String branchName;
    private String visitDate;
    private String session;
    private String slotRange;
    private Integer queueNo;
    private Boolean pdfReady;
    private Boolean canCancel;
    private String cancelBlockedReason;
}
