package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class AppointmentAdminResponse {
    private Long id;
    private String code;
    private AppointmentStatus status;

    private Long branchId;
    private String branchName;

    private Long specialtyId;
    private String specialtyName;

    private Long doctorId;
    private String doctorName;

    private LocalDate visitDate;
    private BranchSessionType session;
    private Integer queueNo;
    private Integer slotMinutes;
    private LocalTime etaStart;
    private LocalTime etaEnd;

    private String patientFullName;
    private String patientPhone;
    private String patientEmail;
    private String reasonForVisit;
    private String visitType;
    // Legacy DB names are kept for compatibility; reception* exposes current product language.
    private String triagePriority;
    private String triageNote;
    private String receptionPriority;
    private String receptionNote;
    private String insuranceNote;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private Double heightCm;
    private Double weightKg;
    private Double temperatureC;
    private Integer pulse;
    private Integer systolicBp;
    private Integer diastolicBp;
    private Integer respiratoryRate;
    private Integer spo2;
    private LocalDateTime intakeCompletedAt;
    private String intakeCompletedByName;

    private Long processingById;
    private String processingByName;
    private LocalDateTime processingStartedAt;
    private LocalDateTime processingExpiresAt;

    private LocalDateTime confirmedAt;
    private String confirmedByName;

    private LocalDateTime checkedInAt;
    private String checkedInByName;

    private LocalDateTime noShowMarkedAt;
    private String noShowMarkedByName;
    private String noShowNote;

    private Boolean followUpPending;
    private Long rescheduledFromAppointmentId;
    private Long rescheduledToAppointmentId;
    private Long patientId;
    private AppointmentSourceType sourceType;
    private ArrivalStatus arrivalStatus;
    private Integer receptionQueueNo;
    private LocalDateTime arrivedAt;
    private String arrivedByName;

    private Long activeEncounterId;
    private String activeEncounterCode;
    private EncounterStatus activeEncounterStatus;
    private Integer serviceOrderCount;
    private Integer pendingPaymentOrderCount;
    private Integer waitingResultItemCount;
    private Integer completedResultItemCount;
    private Integer issuedPrescriptionCount;
    private Boolean hasPendingPayment;
    private Boolean hasWaitingResults;
    private Boolean readyForConclusion;
    private Boolean canCreatePrescription;
    private Boolean canComplete;
}
