package com.PrimeCare.PrimeCare.modules.appointment.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldReason;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class PublicRescheduleOfferResponse {
    private Long slotHoldId;
    private AppointmentSlotHoldStatus status;
    private AppointmentSlotHoldReason holdReason;
    private LocalDateTime expiresAt;
    private LocalDateTime contactRequestedAt;
    private Boolean canAccept;
    private Boolean canRequestContact;
    private Boolean canCancel;
    private String message;
    private AppointmentSummary oldAppointment;
    private AppointmentSummary heldSlot;
    private Long newAppointmentId;

    @Data
    @Builder
    public static class AppointmentSummary {
        private String appointmentCode;
        private String doctorName;
        private String branchName;
        private String specialtyName;
        private LocalDate visitDate;
        private BranchSessionType session;
        private LocalTime startTime;
        private LocalTime endTime;
    }
}
