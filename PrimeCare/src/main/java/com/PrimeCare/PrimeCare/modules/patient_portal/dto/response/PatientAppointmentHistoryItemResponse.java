package com.PrimeCare.PrimeCare.modules.patient_portal.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PatientAppointmentHistoryItemResponse {
    private Long id;
    private String code;
    private String status;
    private String visitDate;
    private String session;
    private String branchName;
    private String specialtyName;
    private String doctorName;
    private String etaStart;
    private String etaEnd;
    private String createdAt;
    private Boolean canCancel;
    private String cancelBlockedReason;
}
