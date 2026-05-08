package com.PrimeCare.PrimeCare.modules.notification.repository;

import com.PrimeCare.PrimeCare.modules.notification.entity.Notification;
import com.PrimeCare.PrimeCare.shared.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByAppointment_IdAndTemplateCodeAndStatusIn(Long appointmentId, String templateCode, Collection<NotificationStatus> statuses);
}
