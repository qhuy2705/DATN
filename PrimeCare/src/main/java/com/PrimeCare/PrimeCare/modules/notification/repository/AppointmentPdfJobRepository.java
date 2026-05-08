package com.PrimeCare.PrimeCare.modules.notification.repository;

import com.PrimeCare.PrimeCare.modules.notification.entity.AppointmentPdfJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppointmentPdfJobRepository extends JpaRepository<AppointmentPdfJob, Long> {
    Optional<AppointmentPdfJob> findByAppointmentId(Long appointmentId);
}