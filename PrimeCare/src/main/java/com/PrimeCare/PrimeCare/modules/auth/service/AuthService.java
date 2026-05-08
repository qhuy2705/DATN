package com.PrimeCare.PrimeCare.modules.auth.service;

import com.PrimeCare.PrimeCare.modules.auth.dto.request.LoginRequest;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.AuthIssueResult;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.CurrentUserResponse;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.TokenResponse;
import com.PrimeCare.PrimeCare.modules.auth.entity.AccessTokenBlacklist;
import com.PrimeCare.PrimeCare.modules.auth.entity.RefreshToken;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.AccessTokenBlacklistRepository;
import com.PrimeCare.PrimeCare.modules.auth.repository.RefreshTokenRepository;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.auth.security.JwtService;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.enums.StaffStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CURRENT_PASSWORD_INCORRECT_MESSAGE = "Mật khẩu hiện tại không chính xác";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenBlacklistRepository blacklistRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.refreshTokenDays}")
    private int refreshTokenDays;

    private final SecureRandom secureRandom = new SecureRandom();

    public long getRefreshMaxAgeSeconds() {
        return refreshTokenDays * 86400L;
    }

    public AuthIssueResult login(LoginRequest req, String userAgent, String ip) {
        User user = userRepository.findByEmailOrPhone(req.getIdentifier().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        ensureUserCanAuthenticate(user);

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        TokenResponse res = issueAccess(user);
        String familyId = java.util.UUID.randomUUID().toString().replace("-", "");
        String refresh = createAndStoreRefreshToken(user, familyId, userAgent, ip);

        return new AuthIssueResult(res, refresh);
    }

    @Transactional
    public AuthIssueResult refresh(String refreshToken, String userAgent, String ip) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_MISSING);
        }

        RefreshToken rt = refreshTokenRepository.findWithLockByToken(refreshToken.trim())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_TOKEN_INVALID));

        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_EXPIRED);
        }

        User user = rt.getUser();
        ensureUserCanAuthenticate(user);

        if (rt.isRevoked()) {
            handleRefreshReuseDetected(user.getId(), rt.getFamilyId());
            throw new ApiException(
                    ErrorCode.AUTH_REFRESH_REVOKED,
                    "Refresh token reuse detected. Session has been revoked. Please login again."
            );
        }

        String newRefresh = generateSecureToken();
        LocalDateTime now = LocalDateTime.now();

        RefreshToken newRt = RefreshToken.builder()
                .user(user)
                .token(newRefresh)
                .expiresAt(now.plusDays(refreshTokenDays))
                .revoked(false)
                .familyId(rt.getFamilyId())
                .userAgent(userAgent)
                .ipAddress(ip)
                .build();

        refreshTokenRepository.save(newRt);

        rt.setRevoked(true);
        rt.setRevokedAt(now);
        rt.setReplacedByToken(newRefresh);
        refreshTokenRepository.save(rt);

        TokenResponse res = issueAccess(user);
        return new AuthIssueResult(res, newRefresh);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        ensureUserCanAuthenticate(user);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    CURRENT_PASSWORD_INCORRECT_MESSAGE,
                    Map.of("fields", Map.of("currentPassword", CURRENT_PASSWORD_INCORRECT_MESSAGE))
            );
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Mật khẩu mới không được trùng mật khẩu hiện tại");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
            user.setStatus(resolveActiveStatus(user));
        }
        userRepository.save(user);

        logoutAllSessions(user.getId());
    }

    @Transactional
    public void logoutByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return;
        }

        refreshTokenRepository.findWithLockByToken(refreshToken.trim()).ifPresent(rt -> {
            Long userId = rt.getUser().getId();
            String familyId = rt.getFamilyId();
            handleRefreshReuseDetected(userId, familyId);
        });
    }

    @Transactional
    public void logoutAllSessions(Long userId) {
        handleRefreshReuseDetected(userId, null);
    }

    private void ensureUserCanAuthenticate(User user) {
        if (user == null) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_DISABLED, "Tài khoản chưa kích hoạt. Vui lòng kiểm tra email hoặc SMS để thiết lập mật khẩu.");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        if (user.getDoctorProfile() != null && user.getDoctorProfile().getStatus() == DoctorStatus.INACTIVE) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_DISABLED, "Bác sĩ đã ngưng hoạt động, tài khoản không thể đăng nhập");
        }

        if (user.getStaffProfile() != null && user.getStaffProfile().getStatus() == StaffStatus.INACTIVE) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_DISABLED, "Nhân sự đã ngưng hoạt động, tài khoản không thể đăng nhập");
        }
    }

    private UserStatus resolveActiveStatus(User user) {
        if (user.getDoctorProfile() != null && user.getDoctorProfile().getStatus() == DoctorStatus.INACTIVE) {
            return UserStatus.INACTIVE;
        }
        if (user.getStaffProfile() != null && user.getStaffProfile().getStatus() == StaffStatus.INACTIVE) {
            return UserStatus.INACTIVE;
        }
        return UserStatus.ACTIVE;
    }

    private void handleRefreshReuseDetected(Long userId, String familyId) {
        if (familyId != null && !familyId.isBlank()) {
            var list = refreshTokenRepository.findAllByUser_IdAndFamilyId(userId, familyId);
            LocalDateTime now = LocalDateTime.now();
            for (RefreshToken t : list) {
                if (!t.isRevoked()) {
                    t.setRevoked(true);
                    t.setRevokedAt(now);
                }
            }
            refreshTokenRepository.saveAll(list);
            return;
        }

        var all = refreshTokenRepository.findAllByUser_Id(userId);
        LocalDateTime now = LocalDateTime.now();
        for (RefreshToken t : all) {
            if (!t.isRevoked()) {
                t.setRevoked(true);
                t.setRevokedAt(now);
            }
        }
        refreshTokenRepository.saveAll(all);
    }

    private TokenResponse issueAccess(User user) {
        String access = jwtService.generateAccessToken(
                String.valueOf(user.getId()),
                Map.of("role", user.getRole().name())
        );

        return TokenResponse.builder()
                .accessToken(access)
                .accessExpiresInSeconds(jwtService.getAccessTokenMinutes() * 60L)
                .user(toCurrentUser(user))
                .build();
    }

    private CurrentUserResponse toCurrentUser(User user) {
        String fullName = null;
        String avatarUrl = null;
        Long branchId = null;
        String branchName = null;

        if (user.getDoctorProfile() != null) {
            fullName = user.getDoctorProfile().getFullName();
            avatarUrl = user.getDoctorProfile().getAvatarUrl();
            if (user.getDoctorProfile().getBranch() != null) {
                branchId = user.getDoctorProfile().getBranch().getId();
                branchName = user.getDoctorProfile().getBranch().getNameVn();
            }
        } else if (user.getStaffProfile() != null) {
            fullName = user.getStaffProfile().getFullName();
            if (user.getStaffProfile().getBranch() != null) {
                branchId = user.getStaffProfile().getBranch().getId();
                branchName = user.getStaffProfile().getBranch().getNameVn();
            }
        } else if (user.getAdminProfile() != null) {
            fullName = user.getAdminProfile().getFullName();
            if (user.getAdminProfile().getBranch() != null) {
                branchId = user.getAdminProfile().getBranch().getId();
                branchName = user.getAdminProfile().getBranch().getNameVn();
            }
        } else if (user.getPatient() != null) {
            fullName = user.getPatient().getFullName();
            avatarUrl = user.getPatient().getAvatarUrl();
        }

        if (fullName == null || fullName.isBlank()) {
            fullName = user.getEmail() != null && !user.getEmail().isBlank()
                    ? user.getEmail()
                    : user.getPhone();
        }

        return CurrentUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(fullName)
                .role(user.getRole().name())
                .avatarUrl(avatarUrl)
                .branchId(branchId)
                .branchName(branchName)
                .patientId(user.getPatient() != null ? user.getPatient().getId() : null)
                .build();
    }

    private String createAndStoreRefreshToken(User user, String familyId, String userAgent, String ip) {
        String refresh = generateSecureToken();
        LocalDateTime exp = LocalDateTime.now().plusDays(refreshTokenDays);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(refresh)
                .expiresAt(exp)
                .revoked(false)
                .familyId(familyId)
                .userAgent(userAgent)
                .ipAddress(ip)
                .build());

        return refresh;
    }

    public void blacklistAccessToken(Long userId, String jti, Instant exp, String reason) {
        if (userId == null || jti == null || jti.isBlank() || exp == null) {
            return;
        }

        if (blacklistRepository.existsByJti(jti)) {
            return;
        }

        blacklistRepository.save(AccessTokenBlacklist.builder()
                .jti(jti)
                .user(User.builder().id(userId).build())
                .expiresAt(LocalDateTime.ofInstant(exp, ZoneId.systemDefault()))
                .reason(reason)
                .build());
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
