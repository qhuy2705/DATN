package com.PrimeCare.PrimeCare.modules.notification.repository;

import com.PrimeCare.PrimeCare.modules.notification.entity.NotificationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, Long> {
}
