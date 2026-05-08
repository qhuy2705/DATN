package com.PrimeCare.PrimeCare.modules.auth.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceConfigTest {

    @Test
    void rejectsShortJwtSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", 15, "dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void rejectsDevelopmentDefaultSecretInProdProfile() {
        String defaultSecret = "dev_jwt_secret_change_me_please_at_least_64_chars_long_1234567890";

        assertThatThrownBy(() -> new JwtService(defaultSecret, 15, "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production JWT secret");
    }
}
