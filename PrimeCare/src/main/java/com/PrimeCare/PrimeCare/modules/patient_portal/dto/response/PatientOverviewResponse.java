package com.PrimeCare.PrimeCare.modules.patient_portal.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PatientOverviewResponse {
    private Long patientId;
    private String patientCode;
    private String fullName;
    private long upcomingAppointments;
    private long totalAppointments;
    private long availableResults;
    private long totalInvoices;
    private Integer profileCompletionPercent;
    private String summaryGeneratedAt;
    private PatientAppointmentHistoryItemResponse nextAppointment;
    private List<PatientAppointmentHistoryItemResponse> recentAppointments;
    private List<PatientResultHistoryItemResponse> recentResults;
    private List<PatientInvoiceHistoryItemResponse> recentInvoices;
}
