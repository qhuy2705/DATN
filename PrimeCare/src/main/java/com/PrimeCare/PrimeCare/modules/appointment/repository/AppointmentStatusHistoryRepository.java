package com.PrimeCare.PrimeCare.modules.appointment.repository;

import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentStatusHistoryRepository extends JpaRepository<AppointmentStatusHistory, Long> {
    List<AppointmentStatusHistory> findByAppointment_IdOrderByChangedAtDesc(Long appointmentId);
}
