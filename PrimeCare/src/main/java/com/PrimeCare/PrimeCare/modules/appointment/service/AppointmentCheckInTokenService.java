package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.record.CheckInTokenPayload;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Service
public class AppointmentCheckInTokenService {

    private final Key key;

    public AppointmentCheckInTokenService(@Value("${app.qr.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(Appointment appointment) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime exp = LocalDateTime.of(appointment.getVisitDate(), LocalTime.of(23, 59, 59));

        return Jwts.builder()
                   .setSubject(appointment.getCode())
                   .setId(UUID.randomUUID().toString().replace("-", ""))
                   .claim("typ", "appointment-checkin")
                   .claim("appointmentId", appointment.getId())
                   .claim("visitDate", appointment.getVisitDate().toString())
                   .setIssuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                   .setExpiration(Date.from(exp.atZone(ZoneId.systemDefault()).toInstant()))
                   .signWith(key, SignatureAlgorithm.HS256)
                   .compact();
    }

    public CheckInTokenPayload verify(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(ErrorCode.APPOINTMENT_CHECKIN_TOKEN_INVALID, "Mã QR check-in không hợp lệ.");
        }
        try {
            Claims claims = Jwts.parserBuilder()
                                .setSigningKey(key)
                                .build()
                                .parseClaimsJws(token)
                                .getBody();

            if (!"appointment-checkin".equals(claims.get("typ", String.class))) {
                throw new ApiException(ErrorCode.APPOINTMENT_CHECKIN_TOKEN_INVALID, "Mã QR check-in không hợp lệ.");
            }

            Long appointmentId = Long.valueOf(String.valueOf(claims.get("appointmentId")));
            String code = claims.getSubject();
            String visitDate = claims.get("visitDate", String.class);

            return new CheckInTokenPayload(appointmentId, code, visitDate);
        } catch (ApiException ex) {
            throw ex;
        } catch (ExpiredJwtException ex) {
            throw new ApiException(ErrorCode.APPOINTMENT_CHECKIN_TOKEN_EXPIRED, "Mã QR check-in đã hết hạn.");
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.APPOINTMENT_CHECKIN_TOKEN_INVALID, "Mã QR check-in không hợp lệ.");
        }
    }
}
