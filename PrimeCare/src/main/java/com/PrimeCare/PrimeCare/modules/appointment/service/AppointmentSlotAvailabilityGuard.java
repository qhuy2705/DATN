package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentSlotHoldRepository;
import com.PrimeCare.PrimeCare.modules.doctor_leave.repository.DoctorLeaveRequestRepository;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentSlotHoldStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.DoctorLeaveRequestStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class AppointmentSlotAvailabilityGuard {

    public static final EnumSet<AppointmentSlotHoldStatus> BLOCKING_HOLD_STATUSES = EnumSet.of(
            AppointmentSlotHoldStatus.HELD,
            AppointmentSlotHoldStatus.CONTACT_REQUESTED
    );

    private static final EnumSet<AppointmentStatus> BLOCKING_APPOINTMENT_STATUSES = EnumSet.of(
            AppointmentStatus.REQUESTED,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN
    );

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotHoldRepository slotHoldRepository;
    private final DoctorLeaveRequestRepository doctorLeaveRequestRepository;

    public void assertSlotAvailable(
            Long doctorId,
            LocalDate visitDate,
            BranchSessionType session,
            LocalTime slotStart,
            LocalTime slotEnd,
            Long excludedAppointmentId,
            Long excludedHoldId
    ) {
        if (isDoctorOnApprovedLeave(doctorId, visitDate, session)) {
            throw new ApiException(ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE, "Bác sĩ đang nghỉ trong buổi đã chọn");
        }
        if (hasAppointmentOverlap(doctorId, visitDate, session, slotStart, slotEnd, excludedAppointmentId)) {
            throw new ApiException(ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE, "Khung giờ đã có lịch hẹn khác");
        }
        if (hasActiveHoldOverlap(doctorId, visitDate, session, slotStart, slotEnd, excludedHoldId)) {
            throw new ApiException(ErrorCode.APPOINTMENT_SLOT_NOT_AVAILABLE, "Khung giờ đang được giữ tạm");
        }
    }

    public boolean hasActiveHoldOverlap(
            Long doctorId,
            LocalDate visitDate,
            BranchSessionType session,
            LocalTime slotStart,
            LocalTime slotEnd,
            Long excludedHoldId
    ) {
        return slotHoldRepository.findActiveOverlapping(
                        doctorId,
                        visitDate,
                        session,
                        slotStart,
                        slotEnd,
                        BLOCKING_HOLD_STATUSES,
                        LocalDateTime.now()
                )
                .stream()
                .anyMatch(hold -> excludedHoldId == null || !excludedHoldId.equals(hold.getId()));
    }

    public boolean hasAppointmentOverlap(
            Long doctorId,
            LocalDate visitDate,
            BranchSessionType session,
            LocalTime slotStart,
            LocalTime slotEnd,
            Long excludedAppointmentId
    ) {
        return appointmentRepository.findWithLockByDoctor_IdAndVisitDateAndSessionAndStatusIn(
                        doctorId,
                        visitDate,
                        session,
                        BLOCKING_APPOINTMENT_STATUSES
                )
                .stream()
                .filter(appointment -> excludedAppointmentId == null || !excludedAppointmentId.equals(appointment.getId()))
                .anyMatch(appointment -> overlaps(appointment, slotStart, slotEnd));
    }

    public boolean isDoctorOnApprovedLeave(Long doctorId, LocalDate visitDate, BranchSessionType session) {
        return doctorLeaveRequestRepository
                .findByDoctor_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        doctorId,
                        DoctorLeaveRequestStatus.APPROVED,
                        visitDate,
                        visitDate
                )
                .stream()
                .anyMatch(leave -> isSessionCovered(
                        visitDate,
                        session,
                        leave.getStartDate(),
                        leave.getEndDate(),
                        leave.getStartSession(),
                        leave.getEndSession()
                ));
    }

    private boolean overlaps(Appointment appointment, LocalTime slotStart, LocalTime slotEnd) {
        LocalTime appointmentStart = appointment.getEtaStart();
        LocalTime appointmentEnd = appointment.getEtaEnd();
        if (appointmentStart == null) {
            return false;
        }
        if (appointmentEnd == null || !appointmentEnd.isAfter(appointmentStart)) {
            appointmentEnd = appointmentStart.plusMinutes(
                    appointment.getSlotMinutes() != null && appointment.getSlotMinutes() > 0
                            ? appointment.getSlotMinutes()
                            : AppointmentAvailabilityService.FIXED_SLOT_MINUTES
            );
        }
        return appointmentStart.isBefore(slotEnd) && appointmentEnd.isAfter(slotStart);
    }

    private boolean isSessionCovered(
            LocalDate targetDate,
            BranchSessionType targetSession,
            LocalDate startDate,
            LocalDate endDate,
            BranchSessionType startSession,
            BranchSessionType endSession
    ) {
        if (targetDate.isBefore(startDate) || targetDate.isAfter(endDate)) {
            return false;
        }
        if (startDate.equals(endDate)) {
            return targetSession.ordinal() >= startSession.ordinal()
                    && targetSession.ordinal() <= endSession.ordinal();
        }
        if (targetDate.equals(startDate)) {
            return targetSession.ordinal() >= startSession.ordinal();
        }
        if (targetDate.equals(endDate)) {
            return targetSession.ordinal() <= endSession.ordinal();
        }
        return true;
    }
}
