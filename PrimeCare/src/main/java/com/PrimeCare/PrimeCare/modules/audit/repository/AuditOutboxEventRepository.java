package com.PrimeCare.PrimeCare.modules.audit.repository;

import com.PrimeCare.PrimeCare.modules.audit.entity.AuditOutboxEvent;
import com.PrimeCare.PrimeCare.modules.audit.entity.AuditOutboxEventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AuditOutboxEventRepository extends JpaRepository<AuditOutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e
            from AuditOutboxEvent e
            where e.status in :statuses
            order by e.createdAt asc
            """)
    List<AuditOutboxEvent> findPublishableForUpdate(
            @Param("statuses") Collection<AuditOutboxEventStatus> statuses,
            Pageable pageable
    );
}
