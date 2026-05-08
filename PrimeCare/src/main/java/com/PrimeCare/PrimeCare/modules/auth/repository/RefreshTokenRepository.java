package com.PrimeCare.PrimeCare.modules.auth.repository;

import com.PrimeCare.PrimeCare.modules.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findWithLockByToken(String token);

    long deleteByUser_Id(Long userId);

    long deleteByExpiresAtBefore(LocalDateTime now);

    List<RefreshToken> findAllByUser_IdAndFamilyId(Long userId, String familyId);

    List<RefreshToken> findAllByUser_Id(Long userId);
}