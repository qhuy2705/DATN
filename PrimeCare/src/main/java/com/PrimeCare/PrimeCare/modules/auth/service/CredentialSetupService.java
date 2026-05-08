package com.PrimeCare.PrimeCare.modules.auth.service;

import com.PrimeCare.PrimeCare.modules.auth.dto.response.CredentialSetupDeliveryResponse;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.CredentialSetupTokenInfoResponse;
import com.PrimeCare.PrimeCare.modules.auth.entity.CredentialSetupToken;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.CredentialSetupTokenRepository;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.shared.enums.CredentialSetupTokenPurpose;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class CredentialSetupService {

    private final CredentialSetupTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AccountCredentialNotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.credential-token-minutes:30}")
    private long credentialTokenMinutes;

    @Transactional
    public CredentialSetupDeliveryResponse createAndDispatch(User user,
                                                             CredentialSetupTokenPurpose purpose,
                                                             Long requestedByUserId) {
        revokeOutstanding(user.getId(), purpose);
        String rawToken = generateSecureToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(credentialTokenMinutes);

        CredentialSetupToken token = CredentialSetupToken.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .purpose(purpose)
                .deliveryTarget(user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : user.getPhone())
                .requestedByUserId(requestedByUserId)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();
        tokenRepository.save(token);

        AccountCredentialNotificationService.DeliveryResult result = notificationService.sendSetupLink(
                resolveFullName(user), user.getEmail(), user.getPhone(), purpose, rawToken
        );
        token.setDeliveryChannel(result.getDeliveryChannel());
        tokenRepository.save(token);

        return CredentialSetupDeliveryResponse.builder()
                .deliveryChannel(result.getDeliveryChannel())
                .maskedDestination(result.getMaskedDestination())
                .delivered(result.isDelivered())
                .setupUrl(result.getSetupUrl())
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional(readOnly = true)
    public CredentialSetupTokenInfoResponse inspect(String rawToken) {
        CredentialSetupToken token = findByRawToken(rawToken);
        User user = token.getUser();
        return CredentialSetupTokenInfoResponse.builder()
                .purpose(token.getPurpose().name())
                .email(maskEmail(user.getEmail()))
                .phone(maskPhone(user.getPhone()))
                .fullName(resolveFullName(user))
                .status(user.getStatus().name())
                .expired(token.getExpiresAt() == null || token.getExpiresAt().isBefore(LocalDateTime.now()))
                .used(token.getUsedAt() != null || token.isRevoked())
                .expiresAt(token.getExpiresAt())
                .build();
    }

    @Transactional
    public void complete(String rawToken, String newPassword) {
        CredentialSetupToken token = findByRawToken(rawToken);
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.AUTH_SETUP_TOKEN_EXPIRED);
        }
        if (token.getUsedAt() != null || token.isRevoked()) {
            throw new ApiException(ErrorCode.AUTH_SETUP_TOKEN_INVALID, "Liên kết đã được sử dụng hoặc đã bị thu hồi");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
            user.setStatus(resolveActivatedStatus(user));
        }
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
        revokeOutstanding(user.getId(), token.getPurpose());
        authService.logoutAllSessions(user.getId());
    }

    @Transactional
    public void requestPasswordReset(String identifier) {
        userRepository.findByEmailOrPhone(identifier == null ? null : identifier.trim())
                .ifPresent(user -> createAndDispatch(user, CredentialSetupTokenPurpose.PASSWORD_RESET, user.getId()));
    }

    private UserStatus resolveActivatedStatus(User user) {
        if (user.getDoctorProfile() != null && user.getDoctorProfile().getStatus() == DoctorStatus.INACTIVE) {
            return UserStatus.INACTIVE;
        }
        if (user.getStaffProfile() != null && user.getStaffProfile().getStatus() != null && "INACTIVE".equals(user.getStaffProfile().getStatus().name())) {
            return UserStatus.INACTIVE;
        }
        return UserStatus.ACTIVE;
    }

    private CredentialSetupToken findByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(ErrorCode.AUTH_SETUP_TOKEN_INVALID);
        }
        return tokenRepository.findByTokenHash(hashToken(rawToken.trim()))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_SETUP_TOKEN_INVALID));
    }

    private void revokeOutstanding(Long userId, CredentialSetupTokenPurpose purpose) {
        LocalDateTime now = LocalDateTime.now();
        for (CredentialSetupToken token : tokenRepository.findAllByUser_IdAndPurposeAndUsedAtIsNull(userId, purpose)) {
            token.setRevoked(true);
            token.setRevokedAt(now);
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể băm token", ex);
        }
    }

    private String resolveFullName(User user) {
        if (user.getDoctorProfile() != null && user.getDoctorProfile().getFullName() != null) {
            return user.getDoctorProfile().getFullName();
        }
        if (user.getStaffProfile() != null && user.getStaffProfile().getFullName() != null) {
            return user.getStaffProfile().getFullName();
        }
        if (user.getAdminProfile() != null && user.getAdminProfile().getFullName() != null) {
            return user.getAdminProfile().getFullName();
        }
        return user.getEmail() != null ? user.getEmail() : user.getPhone();
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.substring(0, 1) + "***" + email.substring(at - 1);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return phone;
        return "***" + phone.substring(phone.length() - 4);
    }
}
