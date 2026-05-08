package com.PrimeCare.PrimeCare.modules.auth.repository;

import com.PrimeCare.PrimeCare.modules.auth.entity.AccessTokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AccessTokenBlacklistRepository extends JpaRepository<AccessTokenBlacklist, Long> {
    boolean existsByJti(String jti);
    long deleteByExpiresAtBefore(LocalDateTime now);
}
