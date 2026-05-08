package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentCheckInTokenServiceTest {

    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    void invalidQrTokenUsesDomainBadRequest() {
        AppointmentCheckInTokenService service = new AppointmentCheckInTokenService(SECRET);

        assertThatThrownBy(() -> service.verify("not-a-jwt"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_CHECKIN_TOKEN_INVALID);
                    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Mã QR check-in không hợp lệ.");
                });
    }

    @Test
    void expiredQrTokenUsesDomainBadRequest() {
        AppointmentCheckInTokenService service = new AppointmentCheckInTokenService(SECRET);
        Instant now = Instant.now();
        String token = Jwts.builder()
                .setSubject("APT-001")
                .claim("typ", "appointment-checkin")
                .claim("appointmentId", 1L)
                .claim("visitDate", LocalDate.now().minusDays(1).toString())
                .setIssuedAt(Date.from(now.minusSeconds(7_200)))
                .setExpiration(Date.from(now.minusSeconds(3_600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        assertThatThrownBy(() -> service.verify(token))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_CHECKIN_TOKEN_EXPIRED);
                    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Mã QR check-in đã hết hạn.");
                });
    }
}
