package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AppointmentQueueService {

    public static final EnumSet<AppointmentStatus> PROJECTED_QUEUE_STATUSES = EnumSet.of(
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.COMPLETED,
            AppointmentStatus.NO_SHOW
    );

    private final AppointmentRepository appointmentRepository;

    @Transactional
    public void recalculateProjectedQueue(Long doctorId, LocalDate visitDate, BranchSessionType session) {
        if (doctorId == null || visitDate == null || session == null) {
            return;
        }

        List<Appointment> appointments = appointmentRepository.findWithLockByDoctor_IdAndVisitDateAndSessionAndStatusIn(
                doctorId,
                visitDate,
                session,
                PROJECTED_QUEUE_STATUSES
        );

        appointments.sort(
                Comparator.comparing(Appointment::getEtaStart, Comparator.nullsLast(Comparator.naturalOrder()))
                          .thenComparing(Appointment::getConfirmedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                          .thenComparing(Appointment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                          .thenComparing(Appointment::getId, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        int index = 1;
        for (Appointment appointment : appointments) {
            Integer nextQueueNo = index;
            if (!Objects.equals(appointment.getQueueNo(), nextQueueNo)) {
                appointment.setQueueNo(nextQueueNo);
            }
            index++;
        }
    }
}