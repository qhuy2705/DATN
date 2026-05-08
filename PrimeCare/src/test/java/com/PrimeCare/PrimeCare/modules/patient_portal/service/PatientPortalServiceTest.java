package com.PrimeCare.PrimeCare.modules.patient_portal.service;

import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentStatusHistoryRepository;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentSelfServiceCancellationPolicy;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentStatusHistoryService;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceStatusHistoryRepository;
import com.PrimeCare.PrimeCare.modules.billing.service.BillingQrService;
import com.PrimeCare.PrimeCare.modules.billing.service.InvoicePdfService;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.request.UpdatePatientSelfProfileRequest;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultStatusHistoryRepository;
import com.PrimeCare.PrimeCare.shared.enums.PatientStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientPortalServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentStatusHistoryService appointmentStatusHistoryService;
    @Mock
    private AppointmentStatusHistoryRepository appointmentStatusHistoryRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceStatusHistoryRepository invoiceStatusHistoryRepository;
    @Mock
    private ServiceResultRepository serviceResultRepository;
    @Mock
    private ServiceResultStatusHistoryRepository serviceResultStatusHistoryRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private BillingQrService billingQrService;
    @Mock
    private InvoicePdfService invoicePdfService;
    @Mock
    private AppointmentSelfServiceCancellationPolicy selfServiceCancellationPolicy;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private PatientPortalService service;

    @BeforeEach
    void setUp() {
        service = new PatientPortalService(
                userRepository,
                appointmentRepository,
                appointmentStatusHistoryService,
                appointmentStatusHistoryRepository,
                invoiceRepository,
                invoiceStatusHistoryRepository,
                serviceResultRepository,
                serviceResultStatusHistoryRepository,
                fileStorageService,
                billingQrService,
                invoicePdfService,
                selfServiceCancellationPolicy,
                auditLogService,
                passwordEncoder
        );
    }

    @Test
    void getMyProfileRejectsNonPatientRole() {
        User staffUser = User.builder().id(1L).role(UserRole.STAFF).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(staffUser));

        assertThatThrownBy(() -> service.getMyProfile(1L))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED)
                );
    }

    @Test
    void updateMyProfileRequiresCurrentPasswordForEmailChange() {
        User user = patientUser();
        UpdatePatientSelfProfileRequest req = new UpdatePatientSelfProfileRequest();
        req.setEmail("new@example.test");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateMyProfile(user.getId(), req))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Vui lòng nhập mật khẩu hiện tại để thay đổi email hoặc số điện thoại");
                    assertThat(((Map<?, ?>) ex.getDetails().get("fields")).get("currentPassword"))
                            .isEqualTo("Vui lòng nhập mật khẩu hiện tại để thay đổi email hoặc số điện thoại");
                });

        verifyNoInteractions(auditLogService, passwordEncoder);
    }

    @Test
    void updateMyProfileRejectsWrongCurrentPasswordForPhoneChange() {
        User user = patientUser();
        UpdatePatientSelfProfileRequest req = new UpdatePatientSelfProfileRequest();
        req.setPhone("0912345678");
        req.setCurrentPassword("wrong-secret");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-secret", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.updateMyProfile(user.getId(), req))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Mật khẩu hiện tại không chính xác");
                    assertThat(((Map<?, ?>) ex.getDetails().get("fields")).get("currentPassword"))
                            .isEqualTo("Mật khẩu hiện tại không chính xác");
                });

        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateMyProfileDoesNotRequireCurrentPasswordForNonCredentialFields() {
        User user = patientUser();
        UpdatePatientSelfProfileRequest req = new UpdatePatientSelfProfileRequest();
        req.setFullName("Patient Updated");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        var response = service.updateMyProfile(user.getId(), req);

        assertThat(response.getFullName()).isEqualTo("Patient Updated");
        verifyNoInteractions(passwordEncoder);
        verify(auditLogService).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateMyProfileAllowsEmailChangeWithCurrentPassword() {
        User user = patientUser();
        UpdatePatientSelfProfileRequest req = new UpdatePatientSelfProfileRequest();
        req.setEmail("new@example.test");
        req.setCurrentPassword("secret");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(userRepository.findByEmail("new@example.test")).thenReturn(Optional.empty());

        var response = service.updateMyProfile(user.getId(), req);

        assertThat(response.getEmail()).isEqualTo("new@example.test");
        assertThat(user.getEmail()).isEqualTo("new@example.test");

        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).log(
                eq(user),
                eq("UPDATE_PROFILE"),
                eq("PATIENT"),
                eq(user.getPatient().getId()),
                beforeCaptor.capture(),
                afterCaptor.capture()
        );
        assertThat(((Map<?, ?>) beforeCaptor.getValue()).containsKey("currentPassword")).isFalse();
        assertThat(((Map<?, ?>) afterCaptor.getValue()).containsKey("currentPassword")).isFalse();
    }

    private User patientUser() {
        Patient patient = Patient.builder()
                .id(2L)
                .code("P-1")
                .fullName("Patient Test")
                .phone("0900000000")
                .email("old@example.test")
                .status(PatientStatus.ACTIVE)
                .build();
        return User.builder()
                .id(1L)
                .role(UserRole.PATIENT)
                .patient(patient)
                .email("old@example.test")
                .phone("0900000000")
                .passwordHash("hash")
                .build();
    }
}
