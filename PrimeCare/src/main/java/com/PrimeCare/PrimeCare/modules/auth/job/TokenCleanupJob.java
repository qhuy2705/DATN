package com.PrimeCare.PrimeCare.modules.auth.job;

import com.PrimeCare.PrimeCare.modules.auth.repository.AccessTokenBlacklistRepository;
import com.PrimeCare.PrimeCare.modules.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenBlacklistRepository blacklistRepository;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.deleteByExpiresAtBefore(now);
        blacklistRepository.deleteByExpiresAtBefore(now);
    }
}
