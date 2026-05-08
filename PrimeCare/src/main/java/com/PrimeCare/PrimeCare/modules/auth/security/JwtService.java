package com.PrimeCare.PrimeCare.modules.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private static final String DEV_DEFAULT_SECRET = "dev_jwt_secret_change_me_please_at_least_64_chars_long_1234567890";

    private final Key key;
    private final int accessTokenMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.accessTokenMinutes}") int accessTokenMinutes,
            @Value("${spring.profiles.active:}") String activeProfiles
    ) {
        validateSecret(secret, activeProfiles);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public int getAccessTokenMinutes() {
        return accessTokenMinutes;
    }

    public String generateAccessToken(String userId, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessTokenMinutes * 60L);
        String jti = UUID.randomUUID().toString().replace("-", "");

        return Jwts.builder()
                .setSubject(userId)
                .setId(jti)
                .claim("typ", "access")
                .addClaims(extraClaims)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseAndValidate(String jwt) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(jwt)
                .getBody();
    }

    private void validateSecret(String secret, String activeProfiles) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret must be configured");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes for HS256");
        }
        if (isProd(activeProfiles)
                && (DEV_DEFAULT_SECRET.equals(secret)
                || secret.toLowerCase(java.util.Locale.ROOT).contains("change_me"))) {
            throw new IllegalStateException("Production JWT secret must not use a development/default value");
        }
    }

    private boolean isProd(String activeProfiles) {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        for (String profile : activeProfiles.split(",")) {
            if ("prod".equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }
}
