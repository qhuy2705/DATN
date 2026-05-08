package com.PrimeCare.PrimeCare.modules.notification.repository;

import com.PrimeCare.PrimeCare.modules.notification.entity.InternalNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InternalNotificationRepository extends JpaRepository<InternalNotification, Long> {

    @Query("""
            select n
            from InternalNotification n
            where n.recipientUser.id = :recipientUserId
              and (:unreadOnly = false or n.readAt is null)
              and (:type is null or n.type = :type)
              and (:severity is null or n.severity = :severity)
              and (n.expiresAt is null or n.expiresAt > :now)
            order by n.createdAt desc, n.id desc
            """)
    Page<InternalNotification> findForRecipient(
            @Param("recipientUserId") Long recipientUserId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("type") String type,
            @Param("severity") String severity,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            select count(n)
            from InternalNotification n
            where n.recipientUser.id = :recipientUserId
              and n.readAt is null
              and (n.expiresAt is null or n.expiresAt > :now)
            """)
    long countUnreadActive(
            @Param("recipientUserId") Long recipientUserId,
            @Param("now") LocalDateTime now
    );

    Optional<InternalNotification> findByIdAndRecipientUser_Id(Long id, Long recipientUserId);

    boolean existsByRecipientUser_IdAndTypeAndEntityTypeAndEntityId(
            Long recipientUserId,
            String type,
            String entityType,
            Long entityId
    );

    @Modifying
    @Query("""
            update InternalNotification n
            set n.readAt = :readAt,
                n.updatedAt = :readAt
            where n.recipientUser.id = :recipientUserId
              and n.readAt is null
              and n.deleted = false
            """)
    int markAllRead(@Param("recipientUserId") Long recipientUserId, @Param("readAt") LocalDateTime readAt);
}
