package com.PrimeCare.PrimeCare.modules.notification.repository;

import com.PrimeCare.PrimeCare.modules.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByUser_Id(Long userId);
}
