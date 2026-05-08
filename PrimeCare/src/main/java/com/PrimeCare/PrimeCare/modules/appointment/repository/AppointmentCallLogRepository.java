package com.PrimeCare.PrimeCare.modules.appointment.repository;

import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentCallLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentCallLogRepository extends JpaRepository<AppointmentCallLog, Long> {
}
