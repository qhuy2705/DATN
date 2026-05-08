package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.config.PublicLookupOtpProperties;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentCheckInTokenService;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentQueueService;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentSelfServiceCancellationPolicy;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentStatusHistoryService;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.notification.repository.AppointmentPdfJobRepository;
import com.PrimeCare.PrimeCare.modules.notification.service.AppointmentPdfService;
import com.PrimeCare.PrimeCare.modules.notification.service.QrCodeService;
import com.PrimeCare.PrimeCare.modules.publiclookup.dto.response.AppointmentLookupVerifyResponse;
import com.PrimeCare.PrimeCare.modules.publiclookup.entity.PublicLookupAccessToken;
import com.PrimeCare.PrimeCare.modules.publiclookup.entity.PublicLookupOtp;
import com.PrimeCare.PrimeCare.modules.publiclookup.messaging.PublicLookupOtpEmailPublisher;
import com.PrimeCare.PrimeCare.modules.publiclookup.messaging.event.PublicLookupOtpEmailEvent;
import com.PrimeCare.PrimeCare.modules.publiclookup.repository.PublicLookupOtpRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.shared.enums.NotificationChannel;
import com.PrimeCare.PrimeCare.shared.enums.PublicLookupType;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicAppointmentLookupServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private PublicLookupOtpRepository otpRepository;
    @Mock
    private PublicLookupOtpDeliveryService otpDeliveryService;
    @Mock
    private PublicLookupOtpEmailPublisher otpEmailPublisher;
    @Mock
    private PublicLookupAccessTokenService accessTokenService;
    @Mock
    private PublicLookupOtpProperties otpProperties;
    @Mock
    private AppointmentPdfJobRepository appointmentPdfJobRepository;
    @Mock
    private AppointmentQueueService appointmentQueueService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private AppointmentPdfService appointmentPdfService;
    @Mock
    private AppointmentCheckInTokenService appointmentCheckInTokenService;
    @Mock
    private AppointmentStatusHistoryService appointmentStatusHistoryService;
    @Mock
    private AppointmentSelfServiceCancellationPolicy selfServiceCancellationPolicy;
    @Mock
    private QrCodeService qrCodeService;
    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PublicAppointmentLookupService service;

    @Test
    void requestOtpSavesOtpAndPublishesEmailEventWithoutSendingMailSynchronously() {
        Appointment appointment = appointment();
        var deliveryTarget = new PublicLookupOtpDeliveryService.DeliveryTarget("patient@example.test", NotificationChannel.EMAIL);
        when(appointmentRepository.findByCode("APT001")).thenReturn(Optional.of(appointment));
        when(otpDeliveryService.resolveEmail(anyString(), anyString())).thenReturn(deliveryTarget);
        when(otpRepository.findTopByLookupTypeAndReferenceIdAndReferenceCodeOrderByCreatedAtDesc(
                PublicLookupType.APPOINTMENT,
                appointment.getId(),
                appointment.getCode()
        )).thenReturn(Optional.empty());
        when(otpProperties.getTtlSeconds()).thenReturn(300);
        when(otpProperties.getResendCooldownSeconds()).thenReturn(30);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-new-otp");
        when(otpDeliveryService.mask(deliveryTarget)).thenReturn("pa***@example.test");

        var response = service.requestOtp("apt001");

        assertThat(response.getChannel()).isEqualTo("EMAIL");
        assertThat(response.getMaskedDestination()).isEqualTo("pa***@example.test");
        assertThat(response.getExpiresInSeconds()).isEqualTo(300);
        assertThat(response.getResendAvailableInSeconds()).isEqualTo(30);

        ArgumentCaptor<PublicLookupOtp> savedOtpCaptor = ArgumentCaptor.forClass(PublicLookupOtp.class);
        verify(otpRepository).save(savedOtpCaptor.capture());
        PublicLookupOtp savedOtp = savedOtpCaptor.getValue();
        assertThat(savedOtp.getLookupType()).isEqualTo(PublicLookupType.APPOINTMENT);
        assertThat(savedOtp.getReferenceId()).isEqualTo(appointment.getId());
        assertThat(savedOtp.getReferenceCode()).isEqualTo("APT001");
        assertThat(savedOtp.getTargetEmail()).isEqualTo("patient@example.test");
        assertThat(savedOtp.getOtpCode()).isEqualTo("hashed-new-otp");

        ArgumentCaptor<PublicLookupOtpEmailEvent> eventCaptor = ArgumentCaptor.forClass(PublicLookupOtpEmailEvent.class);
        verify(otpEmailPublisher).publishAfterCommit(eventCaptor.capture());
        PublicLookupOtpEmailEvent event = eventCaptor.getValue();
        assertThat(event.lookupType()).isEqualTo("APPOINTMENT");
        assertThat(event.referenceId()).isEqualTo(appointment.getId());
        assertThat(event.referenceCode()).isEqualTo("APT001");
        assertThat(event.patientName()).isEqualTo("Nguyen Van A");
        assertThat(event.toEmail()).isEqualTo("patient@example.test");
        assertThat(event.otpCode()).hasSize(6);
        assertThat(event.ttlSeconds()).isEqualTo(300);
        assertThat(event.resendCooldownSeconds()).isEqualTo(30);
        assertThat(event.requestedAt()).isNotNull();

    }

    @Test
    void verifyOtpRejectsWrongOtpAsPublicLookupBadRequest() {
        Appointment appointment = appointment();
        PublicLookupOtp otp = activeOtp();
        when(appointmentRepository.findByCode("APT001")).thenReturn(Optional.of(appointment));
        when(otpRepository.findTopByLookupTypeAndReferenceIdAndReferenceCodeOrderByCreatedAtDesc(
                PublicLookupType.APPOINTMENT,
                appointment.getId(),
                appointment.getCode()
        )).thenReturn(Optional.of(otp));
        when(otpProperties.getMaxAttempts()).thenReturn(5);
        when(passwordEncoder.matches("000000", "hashed-otp")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyOtp("apt001", "000000"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PUBLIC_LOOKUP_OTP_INVALID);
                    assertThat(ex.getErrorCode()).isNotEqualTo(ErrorCode.AUTH_TOKEN_INVALID);
                    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Mã OTP không đúng. Vui lòng kiểm tra lại.");
                    assertThat(ex.getDetails()).containsEntry("remainingAttempts", 4);
                });

        assertThat(otp.getAttemptCount()).isEqualTo(1);
        verify(otpRepository).save(otp);
        verify(accessTokenService, never()).issue(any(), anyLong(), anyString());
    }

    @Test
    void requestOtpRejectsResendInsideCooldownWindow() {
        Appointment appointment = appointment();
        PublicLookupOtp latestOtp = activeOtp();
        latestOtp.setCreatedAt(LocalDateTime.now().minusSeconds(5));
        var deliveryTarget = new PublicLookupOtpDeliveryService.DeliveryTarget("patient@example.test", NotificationChannel.EMAIL);
        when(appointmentRepository.findByCode("APT001")).thenReturn(Optional.of(appointment));
        when(otpDeliveryService.resolveEmail(anyString(), anyString())).thenReturn(deliveryTarget);
        when(otpRepository.findTopByLookupTypeAndReferenceIdAndReferenceCodeOrderByCreatedAtDesc(
                PublicLookupType.APPOINTMENT,
                appointment.getId(),
                appointment.getCode()
        )).thenReturn(Optional.of(latestOtp));
        when(otpProperties.getResendCooldownSeconds()).thenReturn(30);

        assertThatThrownBy(() -> service.requestOtp("apt001"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(ex.getDetails()).containsKey("resendAvailableInSeconds");
                });

        verify(otpRepository, never()).consumeActiveOtps(any(), anyLong(), anyString(), any());
        verify(otpRepository, never()).save(any(PublicLookupOtp.class));
        verify(otpEmailPublisher, never()).publishAfterCommit(any());
    }

    @Test
    void verifyOtpUsesLatestOtpSoOldOtpAfterResendCannotVerify() {
        Appointment appointment = appointment();
        PublicLookupOtp latestOtp = activeOtp();
        latestOtp.setOtpCode("hashed-new-otp");
        when(appointmentRepository.findByCode("APT001")).thenReturn(Optional.of(appointment));
        when(otpRepository.findTopByLookupTypeAndReferenceIdAndReferenceCodeOrderByCreatedAtDesc(
                PublicLookupType.APPOINTMENT,
                appointment.getId(),
                appointment.getCode()
        )).thenReturn(Optional.of(latestOtp));
        when(otpProperties.getMaxAttempts()).thenReturn(5);
        when(passwordEncoder.matches("111111", "hashed-new-otp")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyOtp("apt001", "111111"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PUBLIC_LOOKUP_OTP_INVALID)
                );

        assertThat(latestOtp.getAttemptCount()).isEqualTo(1);
        verify(accessTokenService, never()).issue(any(), anyLong(), anyString());
    }

    @Test
    void verifyOtpIssuesPublicLookupTokenForCorrectLatestOtp() {
        Appointment appointment = appointment();
        PublicLookupOtp latestOtp = activeOtp();
        PublicLookupAccessToken token = PublicLookupAccessToken.builder()
                .token("lookup-token")
                .lookupType(PublicLookupType.APPOINTMENT)
                .referenceId(appointment.getId())
                .referenceCode(appointment.getCode())
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        when(appointmentRepository.findByCode("APT001")).thenReturn(Optional.of(appointment));
        when(otpRepository.findTopByLookupTypeAndReferenceIdAndReferenceCodeOrderByCreatedAtDesc(
                PublicLookupType.APPOINTMENT,
                appointment.getId(),
                appointment.getCode()
        )).thenReturn(Optional.of(latestOtp));
        when(otpProperties.getMaxAttempts()).thenReturn(5);
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);
        when(accessTokenService.issue(PublicLookupType.APPOINTMENT, appointment.getId(), appointment.getCode()))
                .thenReturn(token);

        AppointmentLookupVerifyResponse response = service.verifyOtp("apt001", "123456");

        assertThat(response.getAccessToken()).isEqualTo("lookup-token");
        assertThat(latestOtp.getConsumedAt()).isNotNull();
        verify(otpRepository).save(latestOtp);
    }

    @Test
    void verifyOtpRejectsExpiredOtpAsPublicLookupBadRequest() {
        Appointment appointment = appointment();
        PublicLookupOtp otp = activeOtp();
        otp.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(appointmentRepository.findByCode("APT001")).thenReturn(Optional.of(appointment));
        when(otpRepository.findTopByLookupTypeAndReferenceIdAndReferenceCodeOrderByCreatedAtDesc(
                PublicLookupType.APPOINTMENT,
                appointment.getId(),
                appointment.getCode()
        )).thenReturn(Optional.of(otp));
        when(otpProperties.getMaxAttempts()).thenReturn(5);

        assertThatThrownBy(() -> service.verifyOtp("apt001", "123456"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PUBLIC_LOOKUP_OTP_EXPIRED);
                    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Mã OTP đã hết hạn. Vui lòng gửi lại mã mới.");
                });

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(otpRepository, never()).save(any(PublicLookupOtp.class));
        verify(accessTokenService, never()).issue(any(), anyLong(), anyString());
    }

    @Test
    void verifyOtpRejectsMaxAttemptsAsPublicLookupTooManyRequests() {
        Appointment appointment = appointment();
        PublicLookupOtp otp = activeOtp();
        otp.setAttemptCount(5);
        when(appointmentRepository.findByCode("APT001")).thenReturn(Optional.of(appointment));
        when(otpRepository.findTopByLookupTypeAndReferenceIdAndReferenceCodeOrderByCreatedAtDesc(
                PublicLookupType.APPOINTMENT,
                appointment.getId(),
                appointment.getCode()
        )).thenReturn(Optional.of(otp));
        when(otpProperties.getMaxAttempts()).thenReturn(5);

        assertThatThrownBy(() -> service.verifyOtp("apt001", "123456"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PUBLIC_LOOKUP_OTP_LOCKED);
                    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getMessage()).isEqualTo("Mã OTP đã bị khóa do nhập sai quá nhiều lần. Vui lòng gửi lại mã mới.");
                    assertThat(ex.getDetails()).containsEntry("remainingAttempts", 0);
                });

        assertThat(otp.getConsumedAt()).isNotNull();
        verify(otpRepository).save(otp);
        verify(accessTokenService, never()).issue(any(), anyLong(), anyString());
    }

    private Appointment appointment() {
        return Appointment.builder()
                .id(7L)
                .code("APT001")
                .patientFullName("Nguyen Van A")
                .patientEmail("patient@example.test")
                .build();
    }

    private PublicLookupOtp activeOtp() {
        return PublicLookupOtp.builder()
                .lookupType(PublicLookupType.APPOINTMENT)
                .referenceId(7L)
                .referenceCode("APT001")
                .targetEmail("patient@example.test")
                .otpCode("hashed-otp")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attemptCount(0)
                .build();
    }

}
