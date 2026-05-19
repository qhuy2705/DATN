package com.PrimeCare.PrimeCare.modules.bookingemailotp.service;

import com.PrimeCare.PrimeCare.config.BookingEmailOtpProperties;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.dto.response.BookingEmailOtpRequestResponse;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.dto.response.BookingEmailOtpVerifyResponse;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.entity.BookingEmailOtp;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.entity.BookingEmailOtpPurpose;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.entity.BookingEmailOtpStatus;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.messaging.BookingEmailOtpEmailPublisher;
import com.PrimeCare.PrimeCare.modules.bookingemailotp.repository.BookingEmailOtpRepository;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingEmailOtpService {

    private static final BookingEmailOtpPurpose PURPOSE = BookingEmailOtpPurpose.GUEST_BOOKING_EMAIL_VERIFICATION;
    private static final List<BookingEmailOtpStatus> ACTIVE_STATUSES = List.of(
            BookingEmailOtpStatus.PENDING,
            BookingEmailOtpStatus.VERIFIED
    );

    private final BookingEmailOtpRepository otpRepository;
    private final BookingEmailOtpEmailPublisher emailPublisher;
    private final BookingEmailOtpProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public BookingEmailOtpRequestResponse requestOtp(String email, String ipAddress, String userAgent) {
        String normalizedEmail = normalizeEmail(email);
        LocalDateTime now = LocalDateTime.now();

        enforceResendCooldown(normalizedEmail, now);
        otpRepository.expireActiveOtps(
                PURPOSE,
                normalizedEmail,
                ACTIVE_STATUSES,
                BookingEmailOtpStatus.EXPIRED
        );

        String otpCode = generateOtp();
        String verificationId = UUID.randomUUID().toString().replace("-", "");
        BookingEmailOtp otp = BookingEmailOtp.builder()
                .email(normalizedEmail)
                .normalizedEmail(normalizedEmail)
                .verificationId(verificationId)
                .otpHash(passwordEncoder.encode(otpCode))
                .purpose(PURPOSE)
                .status(BookingEmailOtpStatus.PENDING)
                .expiresAt(now.plusSeconds(properties.getTtlSeconds()))
                .attemptCount(0)
                .ipAddress(trimToLength(ipAddress, 64))
                .userAgent(trimToLength(userAgent, 512))
                .build();
        BookingEmailOtp savedOtp = otpRepository.save(otp);
        if (savedOtp == null) {
            savedOtp = otp;
        }

        emailPublisher.publishAfterCommit(
                savedOtp,
                otpCode,
                properties.getTtlSeconds(),
                properties.getResendCooldownSeconds()
        );

        return BookingEmailOtpRequestResponse.builder()
                .verificationId(verificationId)
                .channel("EMAIL")
                .maskedDestination(maskEmail(normalizedEmail))
                .expiresInSeconds(properties.getTtlSeconds())
                .resendAvailableInSeconds(properties.getResendCooldownSeconds())
                .build();
    }

    @Transactional
    public BookingEmailOtpVerifyResponse verifyOtp(String verificationId, String otpValue, Long currentUserId) {
        BookingEmailOtp otp = findLockedOtp(verificationId);
        LocalDateTime now = LocalDateTime.now();

        validatePendingOtp(otp, now);

        String rawOtp = otpValue == null ? "" : otpValue.trim();
        if (!passwordEncoder.matches(rawOtp, otp.getOtpHash())) {
            handleWrongOtp(otp, now);
        }

        String tokenSecret = generateTokenSecret();
        LocalDateTime tokenExpiresAt = now.plusSeconds(properties.getTokenTtlSeconds());
        otp.setStatus(BookingEmailOtpStatus.VERIFIED);
        otp.setVerifiedAt(now);
        otp.setTokenHash(passwordEncoder.encode(tokenSecret));
        otp.setTokenExpiresAt(tokenExpiresAt);
        otpRepository.save(otp);

        markAccountEmailVerifiedIfMatches(currentUserId, otp.getNormalizedEmail(), now);

        return BookingEmailOtpVerifyResponse.builder()
                .bookingEmailVerificationToken(otp.getVerificationId() + "." + tokenSecret)
                .expiresAt(tokenExpiresAt.toString())
                .build();
    }

    @Transactional
    public VerifiedBookingEmailToken validateTokenForBooking(String token, String email) {
        String normalizedEmail = normalizeEmail(email);
        ParsedBookingToken parsed = parseToken(token);
        BookingEmailOtp otp = otpRepository.findByVerificationId(parsed.verificationId())
                .orElseThrow(() -> new ApiException(ErrorCode.BOOKING_EMAIL_TOKEN_INVALID));
        LocalDateTime now = LocalDateTime.now();

        if (otp.getStatus() != BookingEmailOtpStatus.VERIFIED
                || otp.getConsumedAt() != null
                || StringUtil.trimToNull(otp.getTokenHash()) == null) {
            throw new ApiException(ErrorCode.BOOKING_EMAIL_TOKEN_INVALID);
        }
        if (!normalizedEmail.equals(otp.getNormalizedEmail())) {
            throw new ApiException(ErrorCode.BOOKING_EMAIL_TOKEN_INVALID);
        }
        if (otp.getTokenExpiresAt() == null || !otp.getTokenExpiresAt().isAfter(now)) {
            otp.setStatus(BookingEmailOtpStatus.EXPIRED);
            otpRepository.save(otp);
            throw new ApiException(ErrorCode.BOOKING_EMAIL_TOKEN_EXPIRED);
        }
        if (!passwordEncoder.matches(parsed.secret(), otp.getTokenHash())) {
            throw new ApiException(ErrorCode.BOOKING_EMAIL_TOKEN_INVALID);
        }

        return new VerifiedBookingEmailToken(otp);
    }

    @Transactional
    public void consumeForBooking(VerifiedBookingEmailToken verification) {
        if (verification == null || verification.otp() == null) {
            return;
        }
        BookingEmailOtp otp = verification.otp();
        otp.setStatus(BookingEmailOtpStatus.CONSUMED);
        otp.setConsumedAt(LocalDateTime.now());
        otpRepository.save(otp);
    }

    public String normalizeEmail(String email) {
        String trimmed = StringUtil.trimToNull(email);
        if (trimmed == null) {
            throw new ApiException(ErrorCode.BOOKING_EMAIL_VERIFICATION_REQUIRED);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private BookingEmailOtp findLockedOtp(String verificationId) {
        String normalizedVerificationId = StringUtil.trimToNull(verificationId);
        if (normalizedVerificationId == null) {
            throw new ApiException(ErrorCode.BOOKING_EMAIL_OTP_INVALID);
        }
        return otpRepository.findByVerificationId(normalizedVerificationId)
                .orElseThrow(() -> new ApiException(ErrorCode.BOOKING_EMAIL_OTP_INVALID));
    }

    private void validatePendingOtp(BookingEmailOtp otp, LocalDateTime now) {
        int maxAttempts = Math.max(properties.getMaxAttempts(), 1);
        int attemptCount = otp.getAttemptCount() == null ? 0 : otp.getAttemptCount();
        if (attemptCount >= maxAttempts) {
            otp.setStatus(BookingEmailOtpStatus.CONSUMED);
            otp.setConsumedAt(now);
            otpRepository.save(otp);
            throw new ApiException(
                    ErrorCode.BOOKING_EMAIL_OTP_LOCKED,
                    ErrorCode.BOOKING_EMAIL_OTP_LOCKED.getDefaultMessage(),
                    Map.of("remainingAttempts", 0)
            );
        }
        if (otp.getStatus() != BookingEmailOtpStatus.PENDING || otp.getConsumedAt() != null) {
            throw new ApiException(ErrorCode.BOOKING_EMAIL_OTP_INVALID);
        }
        if (otp.getExpiresAt() == null || !otp.getExpiresAt().isAfter(now)) {
            otp.setStatus(BookingEmailOtpStatus.EXPIRED);
            otpRepository.save(otp);
            throw new ApiException(ErrorCode.BOOKING_EMAIL_OTP_EXPIRED);
        }
    }

    private void handleWrongOtp(BookingEmailOtp otp, LocalDateTime now) {
        int maxAttempts = Math.max(properties.getMaxAttempts(), 1);
        int nextAttempt = (otp.getAttemptCount() == null ? 0 : otp.getAttemptCount()) + 1;
        otp.setAttemptCount(nextAttempt);
        int remainingAttempts = Math.max(maxAttempts - nextAttempt, 0);
        if (nextAttempt >= maxAttempts) {
            otp.setStatus(BookingEmailOtpStatus.CONSUMED);
            otp.setConsumedAt(now);
            otpRepository.save(otp);
            throw new ApiException(
                    ErrorCode.BOOKING_EMAIL_OTP_LOCKED,
                    ErrorCode.BOOKING_EMAIL_OTP_LOCKED.getDefaultMessage(),
                    Map.of("remainingAttempts", 0)
            );
        }
        otpRepository.save(otp);
        throw new ApiException(
                ErrorCode.BOOKING_EMAIL_OTP_INVALID,
                ErrorCode.BOOKING_EMAIL_OTP_INVALID.getDefaultMessage(),
                Map.of("remainingAttempts", remainingAttempts)
        );
    }

    private void enforceResendCooldown(String normalizedEmail, LocalDateTime now) {
        otpRepository.findTopByPurposeAndNormalizedEmailOrderByCreatedAtDesc(PURPOSE, normalizedEmail)
                .ifPresent(latestOtp -> {
                    if (!ACTIVE_STATUSES.contains(latestOtp.getStatus())
                            || latestOtp.getConsumedAt() != null
                            || latestOtp.getCreatedAt() == null) {
                        return;
                    }
                    LocalDateTime resendAvailableAt = latestOtp.getCreatedAt()
                            .plusSeconds(properties.getResendCooldownSeconds());
                    int resendAvailableInSeconds = secondsUntil(resendAvailableAt, now);
                    if (resendAvailableInSeconds > 0) {
                        throw new ApiException(
                                ErrorCode.RATE_LIMITED,
                                "Vui lòng chờ " + resendAvailableInSeconds + " giây trước khi gửi lại mã OTP.",
                                Map.of("resendAvailableInSeconds", resendAvailableInSeconds)
                        );
                    }
                });
    }

    private ParsedBookingToken parseToken(String token) {
        String rawToken = StringUtil.trimToNull(token);
        if (rawToken == null) {
            throw new ApiException(ErrorCode.BOOKING_EMAIL_VERIFICATION_REQUIRED);
        }
        String[] parts = rawToken.split("\\.", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ApiException(ErrorCode.BOOKING_EMAIL_TOKEN_INVALID);
        }
        return new ParsedBookingToken(parts[0], parts[1]);
    }

    private void markAccountEmailVerifiedIfMatches(Long currentUserId, String normalizedEmail, LocalDateTime now) {
        if (currentUserId == null || normalizedEmail == null) {
            return;
        }
        userRepository.findById(currentUserId).ifPresent(user -> {
            String accountEmail = normalizeEmailOrNull(user.getEmail());
            if (accountEmail != null && accountEmail.equals(normalizedEmail) && user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(now);
                userRepository.save(user);
            }
        });
    }

    private String normalizeEmailOrNull(String email) {
        String trimmed = StringUtil.trimToNull(email);
        return trimmed != null ? trimmed.toLowerCase(Locale.ROOT) : null;
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String generateTokenSecret() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private int secondsUntil(LocalDateTime target, LocalDateTime now) {
        long millis = Duration.between(now, target).toMillis();
        if (millis <= 0) {
            return 0;
        }
        return (int) ((millis + 999) / 1000);
    }

    private String trimToLength(String value, int maxLength) {
        String trimmed = StringUtil.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(0, at));
        }
        String prefix = email.substring(0, at);
        String domain = email.substring(at);
        if (prefix.length() <= 2) {
            return prefix.charAt(0) + "***" + domain;
        }
        return prefix.substring(0, 2) + "***" + domain;
    }

    private record ParsedBookingToken(String verificationId, String secret) {
    }

    public record VerifiedBookingEmailToken(BookingEmailOtp otp) {
    }
}
