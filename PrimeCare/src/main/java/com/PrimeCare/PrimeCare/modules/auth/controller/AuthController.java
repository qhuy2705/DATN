package com.PrimeCare.PrimeCare.modules.auth.controller;

import com.PrimeCare.PrimeCare.modules.auth.dto.request.ChangePasswordRequest;
import com.PrimeCare.PrimeCare.modules.auth.dto.request.CompleteCredentialSetupRequest;
import com.PrimeCare.PrimeCare.modules.auth.dto.request.ForgotPasswordRequest;
import com.PrimeCare.PrimeCare.modules.auth.dto.request.InspectCredentialSetupTokenRequest;
import com.PrimeCare.PrimeCare.modules.auth.dto.request.LoginRequest;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.request.RegisterPatientAccountRequest;
import com.PrimeCare.PrimeCare.modules.patient_portal.service.PatientAccountService;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.CredentialSetupTokenInfoResponse;
import com.PrimeCare.PrimeCare.modules.auth.dto.response.TokenResponse;
import com.PrimeCare.PrimeCare.modules.auth.security.JwtService;
import com.PrimeCare.PrimeCare.modules.auth.service.AuthService;
import com.PrimeCare.PrimeCare.modules.auth.service.CredentialSetupService;
import com.PrimeCare.PrimeCare.modules.auth.service.RefreshCookieService;
import com.PrimeCare.PrimeCare.shared.common.ApiResponse;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.CookieUtil;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieService refreshCookieService;
    private final JwtService jwtService;
    private final CredentialSetupService credentialSetupService;
    private final PatientAccountService patientAccountService;

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req,
                                            HttpServletRequest http,
                                            HttpServletResponse res) {

        var issue = authService.login(req, http.getHeader("User-Agent"), getIp(http));

        refreshCookieService.setRefreshTokenCookie(
                res,
                issue.refreshToken(),
                authService.getRefreshMaxAgeSeconds()
        );

        return ApiResponse.ok("Logged in", issue.response());
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(HttpServletRequest http, HttpServletResponse res) {
        String refreshToken = CookieUtil.readCookie(http, refreshCookieService.getCookieName());

        if (StringUtil.isBlank(refreshToken)) {
            refreshCookieService.clearRefreshTokenCookie(res);
            throw new ApiException(ErrorCode.AUTH_REFRESH_MISSING);
        }

        try {
            var issue = authService.refresh(refreshToken, http.getHeader("User-Agent"), getIp(http));
            refreshCookieService.setRefreshTokenCookie(res, issue.refreshToken(), authService.getRefreshMaxAgeSeconds());
            return ApiResponse.ok("Refreshed", issue.response());
        } catch (ApiException ex) {
            if (ex.getErrorCode() == ErrorCode.AUTH_REFRESH_REVOKED ||
                    ex.getErrorCode() == ErrorCode.AUTH_REFRESH_EXPIRED ||
                    ex.getErrorCode() == ErrorCode.AUTH_TOKEN_INVALID ||
                    ex.getErrorCode() == ErrorCode.AUTH_ACCOUNT_DISABLED) {

                refreshCookieService.clearRefreshTokenCookie(res);
            }
            throw ex;
        }
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(Authentication authentication,
                                            @Valid @RequestBody ChangePasswordRequest req,
                                            HttpServletResponse res) {
        Long userId = Long.valueOf(authentication.getName());
        authService.changePassword(userId, req.getCurrentPassword(), req.getNewPassword());
        refreshCookieService.clearRefreshTokenCookie(res);
        return ApiResponse.ok("Password changed", null);
    }

    @PostMapping("/password/forgot")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        credentialSetupService.requestPasswordReset(req.getIdentifier());
        return ApiResponse.ok("Nếu tài khoản tồn tại, chúng tôi đã gửi hướng dẫn thiết lập mật khẩu.", null);
    }

    @PostMapping("/password/inspect")
    public ApiResponse<CredentialSetupTokenInfoResponse> inspectSetupToken(
            @Valid @RequestBody InspectCredentialSetupTokenRequest req
    ) {
        return ApiResponse.ok("Token inspected", credentialSetupService.inspect(req.getToken()));
    }

    @PostMapping("/password/complete")
    public ApiResponse<Void> completePasswordSetup(@Valid @RequestBody CompleteCredentialSetupRequest req,
                                                   HttpServletResponse res) {
        credentialSetupService.complete(req.getToken(), req.getNewPassword());
        refreshCookieService.clearRefreshTokenCookie(res);
        return ApiResponse.ok("Password setup completed", null);
    }


    @PostMapping("/patient/register")
    public ApiResponse<TokenResponse> registerPatient(@Valid @RequestBody RegisterPatientAccountRequest req,
                                                      HttpServletRequest http,
                                                      HttpServletResponse res) {
        var issue = patientAccountService.register(req, http.getHeader("User-Agent"), getIp(http));

        refreshCookieService.setRefreshTokenCookie(
                res,
                issue.refreshToken(),
                authService.getRefreshMaxAgeSeconds()
        );

        return ApiResponse.ok("Patient registered", issue.response());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest http, HttpServletResponse res) {

        String refreshToken = CookieUtil.readCookie(http, refreshCookieService.getCookieName());
        if (!StringUtil.isBlank(refreshToken)) {
            authService.logoutByRefreshToken(refreshToken);
        }

        String header = http.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String accessToken = header.substring(7);
            try {
                Claims claims = jwtService.parseAndValidate(accessToken);

                Long userId = Long.parseLong(claims.getSubject());
                String jti = claims.getId();
                Instant exp = claims.getExpiration().toInstant();

                authService.blacklistAccessToken(userId, jti, exp, "logout_current_device");
            } catch (Exception ignored) {

            }
        }

        refreshCookieService.clearRefreshTokenCookie(res);

        return ApiResponse.ok("Logged out", null);
    }

    private String getIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
