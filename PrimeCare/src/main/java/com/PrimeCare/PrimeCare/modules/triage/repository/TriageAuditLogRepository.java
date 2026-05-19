package com.PrimeCare.PrimeCare.modules.triage.repository;

import com.PrimeCare.PrimeCare.modules.triage.entity.TriageAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TriageAuditLogRepository extends JpaRepository<TriageAuditLog, Long> {

    List<TriageAuditLog> findByAppointment_IdOrderByCreatedAtAsc(Long appointmentId);
}
