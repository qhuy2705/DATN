package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.config.PublicLookupOtpProperties;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentCheckInTokenService;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentStatusHistoryService;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentQueueService;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentSelfServiceCancellationPolicy;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.notification.entity.AppointmentPdfJob;
import com.PrimeCare.PrimeCare.modules.notification.repository.AppointmentPdfJobRepository;
import com.PrimeCare.PrimeCare.modules.notification.service.AppointmentPdfService;
import com.PrimeCare.PrimeCare.modules.notification.service.InternalNotificationService;
import com.PrimeCare.PrimeCare.modules.notification.service.QrCodeService;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.AppointmentLookupSummaryResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.AppointmentLookupVerifyResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicLookupOtpResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.entity.PublicLookupAccessToken;
import com.PrimeCare.PrimeCare.modules.publiclookup.entity.PublicLookupOtp;
import com.PrimeCare.PrimeCare.modules.publiclookup.messaging.PublicLookupOtpEmailPublisher;
import com.PrimeCare.PrimeCare.modules.publiclookup.messaging.event.PublicLookupOtpEmailEvent;
import com.PrimeCare.PrimeCare.modules.publiclookup.repository.PublicLookupOtpRepository;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentPdfJobStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.PublicLookupType;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PublicAppointmentLookupService {
    private static final PublicLookupType LOOKUP_TYPE = PublicLookupType.APPOINTMENT;
    private final SecureRandom secureRandom = new SecureRandom();

    private final AppointmentRepository appointmentRepository;
    private final PublicLookupOtpRepository otpRepository;
    private final PublicLookupOtpDeliveryService otpDeliveryService;
    private final PublicLookupOtpEmailPublisher otpEmailPublisher;
    private final PublicLookupAccessTokenService accessTokenService;
    private final PublicLookupOtpProperties otpProperties;
    private final AppointmentPdfJobRepository appointmentPdfJobRepository;
    private final AppointmentQueueService appointmentQueueService;
    private final FileStorageService fileStorageService;
    private final AppointmentPdfService appointmentPdfService;
    private final AppointmentCheckInTokenService appointmentCheckInTokenService;
    private final AppointmentStatusHistoryService appointmentStatusHistoryService;
    private final AppointmentSelfServiceCancellationPolicy selfServiceCancellationPolicy;
    private final QrCodeService qrCodeService;
    private final com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher realtimeEventPublisher;
    private final InternalNotificationService internalNotificationService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional
    public PublicLookupOtpResponse requestOtp(String code) {
        return requestOtp(code, null);
    }

    @Transactional
    public PublicLookupOtpResponse requestOtp(String code, String channel) {
        otpDeliveryService.assertEmailChannel(channel);
        String lookupCode = normalizeCode(code);
        Appointment appointment = resolveAppointmentForOtp(lookupCode);
        var deliveryTarget = otpDeliveryService.resolveEmail(
                appointment.getPatientEmail(),
                "Lịch hẹn này chưa có email để nhận OTP. Vui lòng liên hệ phòng khám."
        );

        LocalDateTime now = LocalDateTime.now();
        enforceResendCooldown(appointment.getId(), lookupCode, now);
        otpRepository.consumeActiveOtps(LOOKUP_TYPE, appointment.getId(), lookupCode, now);

        String otp = generateOtp();
        otpRepository.save(PublicLookupOtp.builder()
                                          .lookupType(LOOKUP_TYPE)
                                          .referenceId(appointment.getId())
                                          .referenceCode(lookupCode)
                                          .targetEmail(deliveryTarget.value())
                                          .otpCode(passwordEncoder.encode(otp))
                                          .expiresAt(now.plusSeconds(otpProperties.getTtlSeconds()))
                                          .attemptCount(0)
                                          .build());

        otpEmailPublisher.publishAfterCommit(PublicLookupOtpEmailEvent.builder()
                .lookupType(LOOKUP_TYPE.name())
                .referenceId(appointment.getId())
                .referenceCode(lookupCode)
                .patientName(appointment.getPatientFullName())
                .toEmail(deliveryTarget.value())
                .otpCode(otp)
                .ttlSeconds(otpProperties.getTtlSeconds())
                .resendCooldownSeconds(otpProperties.getResendCooldownSeconds())
                .requestedAt(Instant.now())
                .build());

        return PublicLookupOtpResponse.builder()
                                      .channel(deliveryTarget.channel().name())
                                      .maskedDestination(otpDeliveryService.mask(deliveryTarget))
                                      .expiresInSeconds(otpProperties.getTtlSeconds())
                                      .resendAvailableInSeconds(otpProperties.getResendCooldownSeconds())
                                      .build();
    }

    @Transactional
    public AppointmentLookupVerifyResponse verifyOtp(String code, String otpValue) {
        String lookupCode = normalizeCode(code);
        Appointment appointment = resolveAppointmentForOtp(lookupCode);
        PublicLookupOtp otp = otpRepository.findTopByLookupTypeAndReferenceIdAndReferenceCodeOrderByCreatedAtDesc(LOOKUP_TYPE, appointment.getId(), lookupCode)
                                           .orElseThrow(() -> new ApiException(ErrorCode.PUBLIC_LOOKUP_OTP_INVALID));
        validateOtp(otp, otpValue);
        otp.setConsumedAt(LocalDateTime.now());
        otpRepository.save(otp);

        PublicLookupAccessToken accessToken = accessTokenService.issue(LOOKUP_TYPE, appointment.getId(), lookupCode);
        return AppointmentLookupVerifyResponse.builder()
                                              .accessToken(accessToken.getToken())
                                              .expiresAt(accessToken.getExpiresAt().toString())
                                              .appointment(toSummary(appointment))
                                              .build();
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(String code, String token) {
        Appointment appointment = appointmentRepository.findByCode(code.trim().toUpperCase())
                                                       .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND, "Không tìm thấy phiếu hẹn"));
        accessTokenService.verify(token, PublicLookupType.APPOINTMENT, appointment.getId());

        AppointmentPdfJob job = appointmentPdfJobRepository.findByAppointmentId(appointment.getId()).orElse(null);
        if (job != null && job.getStatus() == AppointmentPdfJobStatus.COMPLETED && job.getFilePath() != null && !job.getFilePath().isBlank()) {
            return fileStorageService.downloadAsBytes(job.getFilePath());
        }

        String qrToken = appointmentCheckInTokenService.issueToken(appointment);
        byte[] qrPng = qrCodeService.generatePng(qrToken, 280, 280);
        return appointmentPdfService.generateAppointmentPdf(
                appointment,
                qrPng
        );
    }

    private AppointmentLookupSummaryResponse toSummary(Appointment appointment) {
        String cancelBlockedReason = selfServiceCancellationPolicy.getBlockedReason(appointment);
        return AppointmentLookupSummaryResponse.builder()
                                               .code(appointment.getCode())
                                               .status(appointment.getStatus() != null ? appointment.getStatus().name() : "")
                                               .patientFullName(appointment.getPatientFullName())
                                               .doctorName(appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : "")
                                               .specialtyName(appointment.getSpecialty() != null ? appointment.getSpecialty().getNameVn() : "")
                                               .branchName(appointment.getBranch() != null ? appointment.getBranch().getNameVn() : "")
                                               .visitDate(appointment.getVisitDate() != null ? appointment.getVisitDate().format(DateTimeFormatter.ISO_DATE) : "")
                                               .session(appointment.getSession() != null ? appointment.getSession().name() : "")
                                               .slotRange((appointment.getEtaStart() != null ? appointment.getEtaStart().toString() : "")
                                                       + (appointment.getEtaEnd() != null ? " - " + appointment.getEtaEnd() : ""))
                                               .queueNo(appointment.getQueueNo())
                                               .pdfReady(Boolean.TRUE)
                                               .canCancel(cancelBlockedReason == null)
                                               .cancelBlockedReason(cancelBlockedReason)
                                               .build();
    }


    @Transactional
    public com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAppointmentActionResponse cancel(String code, String token, String reason) {
        Appointment appointment = appointmentRepository.findByCode(code.trim().toUpperCase())
                                                       .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND, "Không tìm thấy phiếu hẹn"));
        accessTokenService.verify(token, PublicLookupType.APPOINTMENT, appointment.getId());

        AppointmentStatus previousStatus = appointment.getStatus();
        Map<String, Object> before = snapshotAppointment(appointment);
        selfServiceCancellationPolicy.assertCanCancel(appointment);
        selfServiceCancellationPolicy.applyCancellation(
                appointment,
                null,
                reason,
                "Patient self-service cancellation"
        );
        Appointment saved = appointmentRepository.save(appointment);
        appointmentStatusHistoryService.record(saved, previousStatus, saved.getStatus(), null, saved.getCancelReason());

        if (saved.getDoctor() != null && saved.getVisitDate() != null && saved.getSession() != null) {
            appointmentQueueService.recalculateProjectedQueue(saved.getDoctor().getId(), saved.getVisitDate(), saved.getSession());
            realtimeEventPublisher.publishAppointmentSummaryChanged(saved.getBranch() != null ? saved.getBranch().getId() : null, saved.getVisitDate());
            realtimeEventPublisher.publishAppointmentUpdated(
                    saved.getId(),
                    saved.getBranch() != null ? saved.getBranch().getId() : null,
                    saved.getDoctor() != null ? saved.getDoctor().getId() : null,
                    saved.getVisitDate(),
                    saved.getSession() != null ? saved.getSession().name() : null,
                    previousStatus != null ? previousStatus.name() : null,
                    saved.getStatus() != null ? saved.getStatus().name() : null,
                    saved.getArrivalStatus() != null ? saved.getArrivalStatus().name() : null,
                    saved.getQueueNo(),
                    saved.getReceptionQueueNo(),
                    saved.getArrivedAt() != null ? saved.getArrivedAt().toString() : null,
                    null,
                    saved.getCheckedInAt() != null ? saved.getCheckedInAt().toString() : null,
                    null,
                    saved.getConfirmedAt() != null ? saved.getConfirmedAt().toString() : null,
                    null
            );
        }
        notifyStaffPatientCancelled(saved);

        auditLogService.log(null, "PUBLIC_CANCEL_APPOINTMENT", "APPOINTMENT", saved.getId(), before, snapshotAppointment(saved));

        return com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicAppointmentActionResponse.builder()
                .code(saved.getCode())
                .status(saved.getStatus() != null ? saved.getStatus().name() : null)
                .message("Huy lich thanh cong")
                .build();
    }

    private void notifyStaffPatientCancelled(Appointment appointment) {
        String message = "Bệnh nhân " + appointment.getPatientFullName()
                + " đã tự hủy lịch hẹn " + appointment.getCode() + ".";
        internalNotificationService.notifyRole(
                UserRole.STAFF,
                "PATIENT_APPOINTMENT_CANCELLED",
                InternalNotificationService.SEVERITY_INFO,
                "Bệnh nhân tự hủy lịch",
                message,
                "/app/appointments/" + appointment.getId() + "/process",
                "APPOINTMENT",
                appointment.getId()
        );
        internalNotificationService.notifyRole(
                UserRole.OPERATIONS_ADMIN,
                "PATIENT_APPOINTMENT_CANCELLED",
                InternalNotificationService.SEVERITY_INFO,
                "Bệnh nhân tự hủy lịch",
                message,
                "/app/appointments",
                "APPOINTMENT",
                appointment.getId()
        );
    }

    private void validateOtp(PublicLookupOtp otp, String otpValue) {
        LocalDateTime now = LocalDateTime.now();
        int attemptCount = otp.getAttemptCount() == null ? 0 : otp.getAttemptCount();
        if (attemptCount >= otpProperties.getMaxAttempts()) {
            otp.setConsumedAt(now);
            otpRepository.save(otp);
            throw new ApiException(
                    ErrorCode.PUBLIC_LOOKUP_OTP_LOCKED,
                    ErrorCode.PUBLIC_LOOKUP_OTP_LOCKED.getDefaultMessage(),
                    Map.of("remainingAttempts", 0)
            );
        }
        if (otp.getConsumedAt() != null) {
            throw new ApiException(ErrorCode.PUBLIC_LOOKUP_OTP_INVALID);
        }
        if (otp.getExpiresAt() == null || !otp.getExpiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.PUBLIC_LOOKUP_OTP_EXPIRED);
        }

        String rawOtp = otpValue == null ? "" : otpValue.trim();
        boolean otpMatched = passwordEncoder.matches(rawOtp, otp.getOtpCode());
        if (!otpMatched) {
            int nextAttempt = attemptCount + 1;
            otp.setAttemptCount(nextAttempt);
            int remainingAttempts = Math.max(otpProperties.getMaxAttempts() - nextAttempt, 0);
            if (nextAttempt >= otpProperties.getMaxAttempts()) {
                otp.setConsumedAt(now);
                otpRepository.save(otp);
                throw new ApiException(
                        ErrorCode.PUBLIC_LOOKUP_OTP_LOCKED,
                        ErrorCode.PUBLIC_LOOKUP_OTP_LOCKED.getDefaultMessage(),
                        Map.of("remainingAttempts", 0)
                );
            }
            otpRepository.save(otp);

            throw new ApiException(
                    ErrorCode.PUBLIC_LOOKUP_OTP_INVALID,
                    ErrorCode.PUBLIC_LOOKUP_OTP_INVALID.getDefaultMessage(),
                    Map.of("remainingAttempts", remainingAttempts)
            );
        }
    }

    private Appointment resolveAppointmentForOtp(String lookupCode) {
        return appointmentRepository.findByCode(lookupCode)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "Không thể xử lý mã tra cứu này. Vui lòng kiểm tra lại mã hoặc liên hệ phòng khám."
                ));
    }

    private void enforceResendCooldown(Long appointmentId, String lookupCode, LocalDateTime now) {
        otpRepository.findTopByLookupTypeAndReferenceIdAndReferenceCodeOrderByCreatedAtDesc(LOOKUP_TYPE, appointmentId, lookupCode)
                .ifPresent(latestOtp -> {
                    if (latestOtp.getConsumedAt() != null || latestOtp.getCreatedAt() == null) {
                        return;
                    }
                    LocalDateTime resendAvailableAt = latestOtp.getCreatedAt().plusSeconds(otpProperties.getResendCooldownSeconds());
                    int resendAvailableInSeconds = secondsUntil(resendAvailableAt, now);
                    if (resendAvailableInSeconds > 0) {
                        throw new ApiException(
                                ErrorCode.RATE_LIMITED,
                                "Vui lòng chờ " + resendAvailableInSeconds + " giây trước khi gửi lại mã OTP.",
                                Map.of("resendAvailableInSeconds", resendAvailableInSeconds)
                        );
                    }
                });
    }

    private int secondsUntil(LocalDateTime target, LocalDateTime now) {
        long millis = Duration.between(now, target).toMillis();
        if (millis <= 0) {
            return 0;
        }
        return (int) ((millis + 999) / 1000);
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(999_999) + 1);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Mã tra cứu không hợp lệ");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> snapshotAppointment(Appointment appointment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appointmentId", appointment.getId());
        data.put("code", appointment.getCode());
        data.put("patientId", appointment.getPatient() != null ? appointment.getPatient().getId() : null);
        data.put("status", appointment.getStatus() != null ? appointment.getStatus().name() : null);
        data.put("branchId", appointment.getBranch() != null ? appointment.getBranch().getId() : null);
        data.put("doctorId", appointment.getDoctor() != null ? appointment.getDoctor().getId() : null);
        data.put("specialtyId", appointment.getSpecialty() != null ? appointment.getSpecialty().getId() : null);
        data.put("visitDate", appointment.getVisitDate());
        data.put("session", appointment.getSession() != null ? appointment.getSession().name() : null);
        data.put("cancelledAt", appointment.getCancelledAt());
        data.put("cancelReason", appointment.getCancelReason());
        data.put("cancellationReasonType", appointment.getCancellationReasonType() != null ? appointment.getCancellationReasonType().name() : null);
        return data;
    }
}
