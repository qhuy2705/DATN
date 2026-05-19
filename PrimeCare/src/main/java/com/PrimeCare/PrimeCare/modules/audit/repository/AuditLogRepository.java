package com.PrimeCare.PrimeCare.modules.audit.repository;

import com.PrimeCare.PrimeCare.modules.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByEntityContainingIgnoreCaseAndCreatedAtBetween(
            String entity,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    Page<AuditLog> findByCreatedAtBetween(
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    List<AuditLog> findTop20ByEntityAndEntityIdOrderByCreatedAtDesc(String entity, Long entityId);

    boolean existsByEventId(String eventId);
}
