package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentResponseToken;
import com.PrimeCare.PrimeCare.modules.appointment.messaging.AppointmentResponseFallbackEmailPublisher;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentResponseTokenRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.entity.Specialty;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentContactStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentFollowUpType;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentPatientResponseStatus;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentResponseServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentResponseTokenRepository tokenRepository;
    @Mock
    private AppointmentResponseFallbackEmailPublisher fallbackEmailPublisher;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AppointmentStatusHistoryService appointmentStatusHistoryService;

    @InjectMocks
    private AppointmentResponseService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "responseTokenTtlHours", 8L);
    }

    @Test
    void sendFallbackEmailSavesHashedTokenAndPublishesFallbackEmail() {
        Appointment appointment = appointment();
        when(tokenRepository.save(any(AppointmentResponseToken.class))).thenAnswer(invocation -> {
            AppointmentResponseToken token = invocation.getArgument(0);
            token.setId(99L);
            return token;
        });

        service.sendFallbackEmail(appointment, User.builder().id(10L).build(), "Không nghe máy");

        AppointmentResponseToken saved = savedToken();
        assertThat(saved.getAppointment()).isSameAs(appointment);
        assertThat(saved.getTokenHash()).hasSize(64);
        assertThat(saved.getTokenHash()).isNotEqualTo(String.valueOf(appointment.getId()));
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        verify(fallbackEmailPublisher).publishFallbackEmail(
                eq(appointment),
                eq(saved),
                any(String.class)
        );
    }

    @Test
    void keepMarksPatientEmailConfirmedWithoutStaffVerifyingPhone() {
        Appointment appointment = appointment();
        AppointmentResponseToken token = token("raw-token", appointment);
        when(tokenRepository.findByTokenHash(hash("raw-token"))).thenReturn(Optional.of(token));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(tokenRepository.save(token)).thenReturn(token);

        service.keep("raw-token");

        assertThat(appointment.getPatientResponseStatus()).isEqualTo(AppointmentPatientResponseStatus.PATIENT_EMAIL_CONFIRMED);
        assertThat(appointment.getContactStatus()).isEqualTo(AppointmentContactStatus.PHONE_UNREACHABLE);
        assertThat(appointment.getFollowUpPending()).isNotEqualTo(Boolean.TRUE);
        assertThat(appointment.getFollowUpType()).isNull();
        assertThat(token.getUsedAction()).isEqualTo("KEEP");
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void requestRecallMarksPhoneConfirmedByEmailButNotStaffVerified() {
        Appointment appointment = appointment();
        AppointmentResponseToken token = token("raw-token", appointment);
        when(tokenRepository.findByTokenHash(hash("raw-token"))).thenReturn(Optional.of(token));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(tokenRepository.save(token)).thenReturn(token);

        service.requestRecall("raw-token");

        assertThat(appointment.getContactStatus()).isEqualTo(AppointmentContactStatus.PHONE_CONFIRMED_BY_EMAIL);
        assertThat(appointment.getContactStatus()).isNotEqualTo(AppointmentContactStatus.PHONE_STAFF_VERIFIED);
        assertThat(appointment.getPatientResponseStatus()).isEqualTo(AppointmentPatientResponseStatus.WAITING_STAFF_RECALL);
        assertThat(appointment.getFollowUpPending()).isTrue();
        assertThat(appointment.getFollowUpType()).isEqualTo(AppointmentFollowUpType.PATIENT_CONTACT_REQUESTED);
    }

    @Test
    void updatePhoneUpdatesSnapshotAndWaitsStaffRecall() {
        Appointment appointment = appointment();
        AppointmentResponseToken token = token("raw-token", appointment);
        when(tokenRepository.findByTokenHash(hash("raw-token"))).thenReturn(Optional.of(token));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(tokenRepository.save(token)).thenReturn(token);

        service.updatePhone("raw-token", "0911222333");

        assertThat(appointment.getPatientPhone()).isEqualTo("0911222333");
        assertThat(appointment.getContactStatus()).isEqualTo(AppointmentContactStatus.PHONE_UPDATED_BY_PATIENT);
        assertThat(appointment.getPatientResponseStatus()).isEqualTo(AppointmentPatientResponseStatus.WAITING_STAFF_RECALL);
        assertThat(appointment.getFollowUpPending()).isTrue();
        assertThat(appointment.getFollowUpType()).isEqualTo(AppointmentFollowUpType.PATIENT_CONTACT_REQUESTED);
    }

    @Test
    void cancelActionCancelsAppointmentWithoutViolationSideEffect() {
        Appointment appointment = appointment();
        AppointmentResponseToken token = token("raw-token", appointment);
        when(tokenRepository.findByTokenHash(hash("raw-token"))).thenReturn(Optional.of(token));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(tokenRepository.save(token)).thenReturn(token);

        service.cancel("raw-token");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getPatientResponseStatus()).isEqualTo(AppointmentPatientResponseStatus.CANCELLED_BY_EMAIL_RESPONSE);
        verify(appointmentStatusHistoryService).record(
                eq(appointment),
                eq(AppointmentStatus.REQUESTED),
                eq(AppointmentStatus.CANCELLED),
                eq(null),
                any(String.class)
        );
    }

    @Test
    void invalidOrExpiredTokenRejected() {
        when(tokenRepository.findByTokenHash(hash("missing"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inspect("missing"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_RESPONSE_TOKEN_INVALID)
                );

        AppointmentResponseToken expired = token("expired", appointment());
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(hash("expired"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.inspect("expired"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_RESPONSE_TOKEN_EXPIRED)
                );
    }

    private AppointmentResponseToken savedToken() {
        ArgumentCaptor<AppointmentResponseToken> captor = ArgumentCaptor.forClass(AppointmentResponseToken.class);
        verify(tokenRepository).save(captor.capture());
        return captor.getValue();
    }

    private AppointmentResponseToken token(String rawToken, Appointment appointment) {
        return AppointmentResponseToken.builder()
                .id(50L)
                .appointment(appointment)
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().plusHours(2))
                .build();
    }

    private Appointment appointment() {
        Branch branch = Branch.builder().id(1L).nameVn("PrimeCare").build();
        Specialty specialty = Specialty.builder().id(2L).nameVn("Nội tổng quát").build();
        DoctorProfile doctor = DoctorProfile.builder().id(3L).fullName("Dr Test").build();
        return Appointment.builder()
                .id(10L)
                .code("APT-10")
                .status(AppointmentStatus.REQUESTED)
                .branch(branch)
                .specialty(specialty)
                .doctor(doctor)
                .visitDate(LocalDate.now().plusDays(1))
                .session(BranchSessionType.AM)
                .etaStart(LocalTime.of(8, 0))
                .etaEnd(LocalTime.of(8, 30))
                .patientFullName("Nguyen Van A")
                .patientPhone("0900000000")
                .patientEmail("patient@example.test")
                .contactStatus(AppointmentContactStatus.PHONE_UNREACHABLE)
                .patientResponseStatus(AppointmentPatientResponseStatus.NEED_PATIENT_RESPONSE)
                .build();
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
