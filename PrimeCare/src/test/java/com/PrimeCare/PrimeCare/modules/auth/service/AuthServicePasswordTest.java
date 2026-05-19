package com.PrimeCare.PrimeCare.modules.auth.service;

import com.PrimeCare.PrimeCare.modules.auth.dto.request.LoginRequest;
import com.PrimeCare.PrimeCare.modules.auth.entity.RefreshToken;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.AccessTokenBlacklistRepository;
import com.PrimeCare.PrimeCare.modules.auth.repository.RefreshTokenRepository;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.auth.security.JwtService;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.enums.UserStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServicePasswordTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AccessTokenBlacklistRepository blacklistRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuthService service;

    @Test
    void changePasswordRejectsWrongCurrentPasswordAsValidationError() {
        User user = activeUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(user.getId(), "wrong-password", "new-password"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Mật khẩu hiện tại không chính xác");
                    assertThat(ex.getDetails()).containsKey("fields");
                    assertThat(((Map<?, ?>) ex.getDetails().get("fields")).get("currentPassword"))
                            .isEqualTo("Mật khẩu hiện tại không chính xác");
                });

        verify(userRepository, never()).save(user);
        verify(refreshTokenRepository, never()).findAllByUser_Id(user.getId());
    }

    @Test
    void loginRejectsWrongPasswordAsInvalidCredentialsUnauthorized() {
        User user = activeUser();
        LoginRequest request = new LoginRequest();
        request.setIdentifier("user@example.test");
        request.setPassword("wrong-password");
        when(userRepository.findByEmailOrPhone("user@example.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(request, "JUnit", "127.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
                    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    void changePasswordUpdatesPasswordAndLogsOutSessions() {
        User user = activeUser();
        List<RefreshToken> sessions = List.of();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(refreshTokenRepository.findAllByUser_Id(user.getId())).thenReturn(sessions);

        service.changePassword(user.getId(), "old-password", "new-password");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(user);
        verify(refreshTokenRepository).findAllByUser_Id(user.getId());
        verify(refreshTokenRepository).saveAll(sessions);
    }

    private User activeUser() {
        return User.builder()
                .id(1L)
                .email("user@example.test")
                .phone("0900000000")
                .passwordHash("old-hash")
                .role(UserRole.STAFF)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
