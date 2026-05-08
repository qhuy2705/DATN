package com.PrimeCare.PrimeCare.modules.publiclookup.job;

import com.PrimeCare.PrimeCare.modules.publiclookup.repository.PublicLookupAccessTokenRepository;
import com.PrimeCare.PrimeCare.modules.publiclookup.repository.PublicLookupOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublicLookupCleanupJob {
    private static final long EXPIRED_RETENTION_HOURS = 24;

    private final PublicLookupOtpRepository otpRepository;
    private final PublicLookupAccessTokenRepository accessTokenRepository;

    @Scheduled(
            initialDelayString = "${app.public-lookup.cleanup.initial-delay-ms:300000}",
            fixedDelayString = "${app.public-lookup.cleanup.fixed-delay-ms:1200000}"
    )
    @Transactional
    public void cleanupExpiredLookupCredentials() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusHours(EXPIRED_RETENTION_HOURS);

        int deletedOtpCount = otpRepository.softDeleteExpiredBefore(cutoff, now);
        int deletedTokenCount = accessTokenRepository.softDeleteExpiredBefore(cutoff, now);

        if (deletedOtpCount > 0 || deletedTokenCount > 0) {
            log.info("Soft-deleted expired public lookup credentials otps={} accessTokens={}", deletedOtpCount, deletedTokenCount);
        }
    }
}
