package com.PrimeCare.PrimeCare.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigHardeningTest {

    @Test
    void prodProfileUsesSecureRefreshCookieAndSecretPlaceholders() throws Exception {
        String prod = Files.readString(Path.of("src/main/resources/application-prod.properties"));

        assertThat(prod).contains("app.security.refreshCookie.secure=true");
        assertThat(prod).contains("app.security.refreshCookie.sameSite=Strict");
        assertThat(prod).contains("app.security.refreshCookie.path=/api/auth/refresh");
        assertThat(prod).contains("app.jwt.secret=${APP_JWT_SECRET}");
        assertThat(prod).doesNotContain("dev_jwt_secret_change_me");
    }

    @Test
    void loginAndPublicOtpRemainRateLimited() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/PrimeCare/PrimeCare/modules/ratelimit/web/RateLimitInterceptor.java"));

        assertThat(source).contains("new RateLimitRule(\"/api/auth/login\", \"POST\"");
        assertThat(source).contains("new RateLimitRule(\"/api/auth/refresh\", \"POST\"");
        assertThat(source).contains("new RateLimitRule(\"/api/auth/patient/register\", \"POST\"");
        assertThat(source).contains("new RateLimitRule(\"/api/auth/password/forgot\", \"POST\"");
        assertThat(source).contains("CREATE_APPOINTMENT");
        assertThat(source).contains("PUBLIC_CONTACT");
        assertThat(source).contains("PUBLIC_LOOKUP_APPOINTMENT_REQUEST_OTP");
        assertThat(source).contains("PUBLIC_LOOKUP_APPOINTMENT_VERIFY_OTP");
        assertThat(source).contains("PUBLIC_LOOKUP_RESULT_REQUEST_OTP");
        assertThat(source).contains("PUBLIC_LOOKUP_RESULT_VERIFY_OTP");
    }

    @Test
    void csrfMitigationIsDocumentedWhenDisabledForStatelessJwt() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/PrimeCare/PrimeCare/config/SecurityConfig.java"));

        assertThat(source).contains("CSRF is disabled because API authorization is stateless JWT");
        assertThat(source).contains("cookie/session-authenticated state-changing endpoints must add CSRF");
    }
}
