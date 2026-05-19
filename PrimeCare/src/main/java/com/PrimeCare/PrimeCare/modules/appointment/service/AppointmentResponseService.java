package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentResponseInfoResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentResponseToken;
import com.PrimeCare.PrimeCare.modules.appointment.messaging.AppointmentResponseFallbackEmailPublisher;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentResponseTokenRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentCancellationReasonType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentContactStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentFollowUpType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentPatientResponseStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.pdf.PdfFormatters;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppointmentResponseService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppointmentRepository appointmentRepository;
    private final AppointmentResponseTokenRepository tokenRepository;
    private final AppointmentResponseFallbackEmailPublisher fallbackEmailPublisher;
    private final AuditLogService auditLogService;
    private final AppointmentStatusHistoryService appointmentStatusHistoryService;

    @Value("${app.appointment.response-token.ttl-hours:8}")
    private long responseTokenTtlHours;

    @Transactional
    public AppointmentResponseToken sendFallbackEmail(Appointment appointment, User staff, String note) {
        if (appointment == null || appointment.getId() == null) {
            throw new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        if (StringUtil.trimToNull(appointment.getPatientEmail()) == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Lịch hẹn chưa có email để gửi phản hồi fallback.");
        }
        if (appointment.getStatus() != AppointmentStatus.REQUESTED) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, "Chỉ gửi fallback xác nhận cho lịch đang chờ xác nhận.");
        }

        IssuedToken issuedToken = issueToken(appointment);
        fallbackEmailPublisher.publishFallbackEmail(
                appointment,
                issuedToken.token(),
                issuedToken.rawToken()
        );
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("appointmentId", appointment.getId());
        auditPayload.put("tokenId", issuedToken.token().getId());
        auditPayload.put("expiresAt", issuedToken.token().getExpiresAt());
        auditPayload.put("note", StringUtil.trimToNull(note));
        auditLogService.log(staff, "SEND_APPOINTMENT_RESPONSE_FALLBACK", "APPOINTMENT", appointment.getId(), null, auditPayload);
        return issuedToken.token();
    }

    @Transactional(readOnly = true)
    public AppointmentResponseInfoResponse inspect(String rawToken) {
        AppointmentResponseToken token = requireValidToken(rawToken);
        return toInfoResponse(token);
    }

    @Transactional
    public AppointmentResponseInfoResponse keep(String rawToken) {
        AppointmentResponseToken token = requireValidToken(rawToken);
        Appointment appointment = requireRequestableAppointment(token);
        Map<String, Object> before = snapshot(appointment);

        appointment.setPatientResponseStatus(AppointmentPatientResponseStatus.PATIENT_EMAIL_CONFIRMED);
        appointmentRepository.save(appointment);
        consume(token, "KEEP");
        auditLogService.log(null, "APPOINTMENT_RESPONSE_KEEP", "APPOINTMENT", appointment.getId(), before, snapshot(appointment));
        return toInfoResponse(token);
    }

    @Transactional
    public AppointmentResponseInfoResponse requestRecall(String rawToken) {
        AppointmentResponseToken token = requireValidToken(rawToken);
        Appointment appointment = requireRequestableAppointment(token);
        Map<String, Object> before = snapshot(appointment);

        appointment.setContactStatus(AppointmentContactStatus.PHONE_CONFIRMED_BY_EMAIL);
        appointment.setPatientResponseStatus(AppointmentPatientResponseStatus.WAITING_STAFF_RECALL);
        markPatientContactFollowUp(appointment);
        appointmentRepository.save(appointment);
        consume(token, "REQUEST_RECALL");
        auditLogService.log(null, "APPOINTMENT_RESPONSE_REQUEST_RECALL", "APPOINTMENT", appointment.getId(), before, snapshot(appointment));
        return toInfoResponse(token);
    }

    @Transactional
    public AppointmentResponseInfoResponse updatePhone(String rawToken, String phone) {
        AppointmentResponseToken token = requireValidToken(rawToken);
        Appointment appointment = requireRequestableAppointment(token);
        String normalizedPhone = StringUtil.trimToNull(phone);
        if (normalizedPhone == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Số điện thoại là bắt buộc.");
        }
        Map<String, Object> before = snapshot(appointment);
        String oldPhone = appointment.getPatientPhone();

        appointment.setPatientPhone(normalizedPhone);
        appointment.setContactStatus(AppointmentContactStatus.PHONE_UPDATED_BY_PATIENT);
        appointment.setPatientResponseStatus(AppointmentPatientResponseStatus.WAITING_STAFF_RECALL);
        markPatientContactFollowUp(appointment);
        appointmentRepository.save(appointment);
        consume(token, "UPDATE_PHONE");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("appointment", snapshot(appointment));
        after.put("oldPhone", oldPhone);
        after.put("newPhone", normalizedPhone);
        auditLogService.log(null, "APPOINTMENT_RESPONSE_UPDATE_PHONE", "APPOINTMENT", appointment.getId(), before, after);
        return toInfoResponse(token);
    }

    @Transactional
    public AppointmentResponseInfoResponse cancel(String rawToken) {
        AppointmentResponseToken token = requireValidToken(rawToken);
        Appointment appointment = requireRequestableAppointment(token);
        Map<String, Object> before = snapshot(appointment);
        AppointmentStatus previousStatus = appointment.getStatus();

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancelReason("Bệnh nhân hủy qua phản hồi email xác nhận liên hệ.");
        appointment.setCancellationReasonType(AppointmentCancellationReasonType.PATIENT_EMAIL_RESPONSE);
        appointment.setPatientResponseStatus(AppointmentPatientResponseStatus.CANCELLED_BY_EMAIL_RESPONSE);
        appointment.setProcessingBy(null);
        appointment.setProcessingStartedAt(null);
        appointment.setProcessingExpiresAt(null);
        appointmentRepository.save(appointment);
        consume(token, "CANCEL");
        appointmentStatusHistoryService.record(
                appointment,
                previousStatus,
                appointment.getStatus(),
                null,
                "Bệnh nhân hủy qua phản hồi email xác nhận liên hệ."
        );
        auditLogService.log(null, "APPOINTMENT_RESPONSE_CANCEL", "APPOINTMENT", appointment.getId(), before, snapshot(appointment));
        return toInfoResponse(token);
    }

    private IssuedToken issueToken(Appointment appointment) {
        String rawToken = generateSecureToken();
        AppointmentResponseToken token = AppointmentResponseToken.builder()
                .appointment(appointment)
                .tokenHash(hashToken(rawToken))
                .expiresAt(resolveExpiresAt(appointment))
                .build();
        return new IssuedToken(rawToken, tokenRepository.save(token));
    }

    private AppointmentResponseToken requireValidToken(String rawToken) {
        String tokenValue = StringUtil.trimToNull(rawToken);
        if (tokenValue == null) {
            throw new ApiException(ErrorCode.APPOINTMENT_RESPONSE_TOKEN_INVALID);
        }
        AppointmentResponseToken token = tokenRepository.findByTokenHash(hashToken(tokenValue))
                .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_RESPONSE_TOKEN_INVALID));
        if (token.getUsedAt() != null) {
            throw new ApiException(ErrorCode.APPOINTMENT_RESPONSE_TOKEN_INVALID);
        }
        if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.APPOINTMENT_RESPONSE_TOKEN_EXPIRED);
        }
        return token;
    }

    private Appointment requireRequestableAppointment(AppointmentResponseToken token) {
        Appointment appointment = token.getAppointment();
        if (appointment == null || appointment.getId() == null) {
            throw new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        if (appointment.getStatus() != AppointmentStatus.REQUESTED) {
            throw new ApiException(ErrorCode.APPOINTMENT_INVALID_STATUS, "Lịch hẹn không còn ở trạng thái chờ xác nhận.");
        }
        return appointment;
    }

    private void consume(AppointmentResponseToken token, String action) {
        token.setUsedAt(LocalDateTime.now());
        token.setUsedAction(action);
        tokenRepository.save(token);
    }

    private LocalDateTime resolveExpiresAt(Appointment appointment) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime defaultExpiry = now.plusHours(responseTokenTtlHours > 0 ? responseTokenTtlHours : 8);
        if (appointment.getVisitDate() == null || appointment.getEtaStart() == null) {
            return defaultExpiry;
        }
        LocalDateTime appointmentStart = appointment.getVisitDate().atTime(appointment.getEtaStart());
        if (!appointmentStart.isAfter(now)) {
            return now.plusMinutes(30);
        }
        LocalDateTime capped = appointmentStart.minusMinutes(30);
        if (capped.isAfter(now.plusMinutes(15)) && capped.isBefore(defaultExpiry)) {
            return capped;
        }
        return appointmentStart.isBefore(defaultExpiry) ? appointmentStart : defaultExpiry;
    }

    private void markPatientContactFollowUp(Appointment appointment) {
        appointment.setFollowUpPending(true);
        appointment.setFollowUpType(AppointmentFollowUpType.PATIENT_CONTACT_REQUESTED);
    }

    private AppointmentResponseInfoResponse toInfoResponse(AppointmentResponseToken token) {
        Appointment appointment = token.getAppointment();
        return AppointmentResponseInfoResponse.builder()
                .appointmentCode(appointment.getCode())
                .status(appointment.getStatus())
                .contactStatus(appointment.getContactStatus())
                .patientResponseStatus(appointment.getPatientResponseStatus())
                .patientFullName(appointment.getPatientFullName())
                .maskedPhone(maskPhone(appointment.getPatientPhone()))
                .maskedEmail(maskEmail(appointment.getPatientEmail()))
                .visitDate(appointment.getVisitDate())
                .etaStart(appointment.getEtaStart())
                .etaEnd(appointment.getEtaEnd())
                .branchName(appointment.getBranch() != null ? PdfFormatters.firstNonBlank(appointment.getBranch().getNameVn(), appointment.getBranch().getNameEn()) : null)
                .specialtyName(appointment.getSpecialty() != null ? PdfFormatters.firstNonBlank(appointment.getSpecialty().getNameVn(), appointment.getSpecialty().getNameEn()) : null)
                .doctorName(appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : null)
                .tokenExpiresAt(token.getExpiresAt())
                .availableActions(token.getUsedAt() == null && appointment.getStatus() == AppointmentStatus.REQUESTED
                        ? List.of("KEEP", "REQUEST_RECALL", "UPDATE_PHONE", "CANCEL")
                        : List.of())
                .build();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private Map<String, Object> snapshot(Appointment appointment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", appointment.getId());
        data.put("code", appointment.getCode());
        data.put("status", appointment.getStatus());
        data.put("patientPhone", appointment.getPatientPhone());
        data.put("contactStatus", appointment.getContactStatus());
        data.put("patientResponseStatus", appointment.getPatientResponseStatus());
        data.put("followUpPending", appointment.getFollowUpPending());
        data.put("followUpType", appointment.getFollowUpType());
        data.put("cancelReason", appointment.getCancelReason());
        data.put("cancellationReasonType", appointment.getCancellationReasonType());
        return data;
    }

    private String maskPhone(String phone) {
        String value = StringUtil.trimToNull(phone);
        if (value == null || value.length() <= 4) {
            return value;
        }
        return "***" + value.substring(value.length() - 4);
    }

    private String maskEmail(String email) {
        String value = StringUtil.trimToNull(email);
        if (value == null) {
            return null;
        }
        int at = value.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? value.substring(at) : "");
        }
        return value.charAt(0) + "***" + value.substring(at);
    }

    private record IssuedToken(String rawToken, AppointmentResponseToken token) {
    }
}
