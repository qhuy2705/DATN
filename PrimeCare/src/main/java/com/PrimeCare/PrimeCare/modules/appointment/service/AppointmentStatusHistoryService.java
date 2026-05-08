package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentStatusHistory;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentStatusHistoryRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AppointmentStatusHistoryService {

    private final AppointmentStatusHistoryRepository repository;

    @Transactional
    public void record(Appointment appointment, AppointmentStatus fromStatus, AppointmentStatus toStatus, User changedBy, String note) {
        if (appointment == null || toStatus == null) return;
        if (fromStatus == toStatus) return;
        repository.save(AppointmentStatusHistory.builder()
                .appointment(appointment)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .changedAt(LocalDateTime.now())
                .note(note)
                .build());
    }
}
