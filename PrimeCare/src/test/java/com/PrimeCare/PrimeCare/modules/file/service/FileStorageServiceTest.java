package com.PrimeCare.PrimeCare.modules.file.service;

import com.PrimeCare.PrimeCare.config.S3Properties;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import com.PrimeCare.PrimeCare.modules.file.repository.FileObjectRepository;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.shared.enums.FileOwnerType;
import com.PrimeCare.PrimeCare.shared.enums.ServiceResultStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private FileObjectRepository fileObjectRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private ServiceResultRepository serviceResultRepository;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService(
                s3Client,
                new S3Properties(),
                fileObjectRepository,
                auditLogService,
                appointmentRepository,
                invoiceRepository,
                prescriptionRepository,
                serviceResultRepository
        );
    }

    @Test
    void uploadAttachmentRejectsVerifiedServiceResultOwner() {
        ServiceResult verifiedResult = ServiceResult.builder()
                .id(21L)
                .status(ServiceResultStatus.VERIFIED)
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "result.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4\n".getBytes(StandardCharsets.UTF_8)
        );
        User technician = User.builder()
                .id(99L)
                .role(UserRole.SERVICE_TECHNICIAN)
                .build();

        when(serviceResultRepository.findById(21L)).thenReturn(Optional.of(verifiedResult));

        assertThatThrownBy(() -> service.uploadAttachment(file, FileOwnerType.SERVICE_RESULT, 21L, technician))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Verified service results cannot be edited.");
                });

        verify(fileObjectRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any());
    }
}
