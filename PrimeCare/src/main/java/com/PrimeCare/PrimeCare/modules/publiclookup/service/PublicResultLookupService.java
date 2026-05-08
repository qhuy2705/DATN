package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.config.PublicLookupOtpProperties;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.PublicLookupOtpResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.ResultLookupSummaryResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.ResultLookupVerifyResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.entity.PublicLookupAccessToken;
import com.PrimeCare.PrimeCare.modules.publiclookup.entity.PublicLookupOtp;
import com.PrimeCare.PrimeCare.modules.publiclookup.messaging.PublicLookupOtpEmailPublisher;
import com.PrimeCare.PrimeCare.modules.publiclookup.messaging.event.PublicLookupOtpEmailEvent;
import com.PrimeCare.PrimeCare.modules.publiclookup.repository.PublicLookupOtpRepository;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.PublicLookupType;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PublicResultLookupService {
    private static final PublicLookupType LOOKUP_TYPE = PublicLookupType.RESULT;
    private final SecureRandom secureRandom = new SecureRandom();

    private final AppointmentRepository appointmentRepository;
    private final EncounterRepository encounterRepository;
    private final ServiceResultRepository serviceResultRepository;
    private final PublicLookupOtpRepository otpRepository;
    private final PublicLookupOtpDeliveryService otpDeliveryService;
    private final PublicLookupOtpEmailPublisher otpEmailPublisher;
    private final PublicLookupAccessTokenService accessTokenService;
    private final PublicLookupOtpProperties otpProperties;
    private final PublicResultPdfService publicResultPdfService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public PublicLookupOtpResponse requestOtp(String code) {
        String lookupCode = normalizeCode(code);
        Encounter encounter = resolveEncounterReadyForPublicLookup(lookupCode);
        Appointment appointment = encounter.getAppointment();
        String patientEmail = appointment != null ? appointment.getPatientEmail() : encounter.getPatientEmailSnapshot();
        var deliveryTarget = otpDeliveryService.resolveEmail(
                patientEmail,
                "Hồ sơ này chưa có email để nhận OTP. Vui lòng liên hệ phòng khám."
        );

        LocalDateTime now = LocalDateTime.now();
        enforceResendCooldown(encounter.getId(), lookupCode, now);
        otpRepository.consumeActiveOtps(LOOKUP_TYPE, encounter.getId(), lookupCode, now);

        String otp = generateOtp();
        otpRepository.save(PublicLookupOtp.builder()
                                          .lookupType(LOOKUP_TYPE)
                                          .referenceId(encounter.getId())
                                          .referenceCode(lookupCode)
                                          .targetEmail(deliveryTarget.value())
                                          .otpCode(passwordEncoder.encode(otp))
                                          .expiresAt(now.plusSeconds(otpProperties.getTtlSeconds()))
                                          .attemptCount(0)
                                          .build());
        otpEmailPublisher.publishAfterCommit(PublicLookupOtpEmailEvent.builder()
                .lookupType(LOOKUP_TYPE.name())
                .referenceId(encounter.getId())
                .referenceCode(lookupCode)
                .patientName(encounter.getPatientFullNameSnapshot())
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
    public ResultLookupVerifyResponse verifyOtp(String code, String otpValue) {
        String lookupCode = normalizeCode(code);
        Encounter encounter = resolveEncounterReadyForPublicLookup(lookupCode);
        PublicLookupOtp otp = otpRepository.findTopByLookupTypeAndReferenceIdAndReferenceCodeOrderByCreatedAtDesc(LOOKUP_TYPE, encounter.getId(), lookupCode)
                                           .orElseThrow(() -> new ApiException(ErrorCode.PUBLIC_LOOKUP_OTP_INVALID));
        validateOtp(otp, otpValue);
        otp.setConsumedAt(LocalDateTime.now());
        otpRepository.save(otp);

        PublicLookupAccessToken accessToken = accessTokenService.issue(LOOKUP_TYPE, encounter.getId(), lookupCode);
        return ResultLookupVerifyResponse.builder()
                                         .accessToken(accessToken.getToken())
                                         .expiresAt(accessToken.getExpiresAt().toString())
                                         .result(toSummary(encounter))
                                         .build();
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(String code, String token) {
        Encounter encounter = resolveEncounterReadyForPublicLookup(code.trim().toUpperCase());
        accessTokenService.verify(token, PublicLookupType.RESULT, encounter.getId());
        List<ServiceResult> results = serviceResultRepository.findByServiceOrderItem_ServiceOrder_Encounter_Id(encounter.getId())
                .stream()
                .filter(this::isEligibleForPublicLookup)
                .toList();
        return publicResultPdfService.generate(encounter, results);
    }

    private Encounter resolveEncounterReadyForPublicLookup(String lookupCode) {
        Encounter encounter = resolveEncounter(lookupCode);
        if (encounter.getStatus() != EncounterStatus.COMPLETED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Kết quả đang chờ bác sĩ kết luận và chưa sẵn sàng để tra cứu.");
        }
        return encounter;
    }

    private Encounter resolveEncounter(String lookupCode) {
        return encounterRepository.findByCode(lookupCode)
                .or(() -> appointmentRepository.findByCode(lookupCode).flatMap(appointment -> encounterRepository.findByAppointment_Id(appointment.getId())))
                .orElseThrow(() -> new ApiException(ErrorCode.ENCOUNTER_NOT_FOUND, "Chưa có kết quả khám cho mã này"));
    }

    private ResultLookupSummaryResponse toSummary(Encounter encounter) {
        List<ServiceResult> results = serviceResultRepository.findByServiceOrderItem_ServiceOrder_Encounter_Id(encounter.getId());
        long completedCount = results.stream().filter(this::isEligibleForPublicLookup).count();
        long verifiedCount = results.stream().filter(it -> it.getStatus() == ServiceResultStatus.VERIFIED).count();
        boolean doctorConcluded = encounter.getStatus() == EncounterStatus.COMPLETED;

        return ResultLookupSummaryResponse.builder()
                                          .appointmentCode(encounter.getAppointment() != null ? encounter.getAppointment().getCode() : "")
                                          .encounterCode(encounter.getCode())
                                          .patientFullName(encounter.getPatientFullNameSnapshot())
                                          .doctorName(encounter.getDoctor() != null ? encounter.getDoctor().getFullName() : "")
                                          .specialtyName(encounter.getSpecialty() != null ? encounter.getSpecialty().getNameVn() : "")
                                          .branchName(encounter.getBranch() != null ? encounter.getBranch().getNameVn() : "")
                                          .visitDate(encounter.getAppointment() != null && encounter.getAppointment().getVisitDate() != null ? encounter.getAppointment().getVisitDate().toString() : "")
                                          .encounterStatus(encounter.getStatus() != null ? encounter.getStatus().name() : "")
                                          .finalDiagnosis(encounter.getFinalDiagnosis())
                                          .conclusion(encounter.getConclusion())
                                          .serviceResultCount(results.size())
                                          .completedResultCount((int) completedCount)
                                          .verifiedResultCount((int) verifiedCount)
                                          .doctorConcluded(doctorConcluded)
                                          .pdfReady(doctorConcluded && verifiedCount > 0)
                                          .build();
    }

    private boolean isEligibleForPublicLookup(ServiceResult result) {
        return result != null && result.getStatus() == ServiceResultStatus.VERIFIED;
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

    private void enforceResendCooldown(Long referenceId, String lookupCode, LocalDateTime now) {
        otpRepository.findTopByLookupTypeAndReferenceIdAndReferenceCodeOrderByCreatedAtDesc(LOOKUP_TYPE, referenceId, lookupCode)
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
}
