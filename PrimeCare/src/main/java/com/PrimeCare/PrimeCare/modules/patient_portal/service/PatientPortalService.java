package com.PrimeCare.PrimeCare.modules.patient_portal.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.entity.AppointmentStatusHistory;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentStatusHistoryRepository;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentSelfServiceCancellationPolicy;
import com.PrimeCare.PrimeCare.modules.appointment.service.AppointmentStatusHistoryService;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.billing.entity.Invoice;
import com.PrimeCare.PrimeCare.modules.billing.entity.InvoiceStatusHistory;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceRepository;
import com.PrimeCare.PrimeCare.modules.billing.repository.InvoiceStatusHistoryRepository;
import com.PrimeCare.PrimeCare.modules.billing.service.BillingQrService;
import com.PrimeCare.PrimeCare.modules.billing.service.InvoicePdfService;
import com.PrimeCare.PrimeCare.modules.file.service.FileStorageService;
import com.PrimeCare.PrimeCare.modules.patient.dto.response.PatientResponse;
import com.PrimeCare.PrimeCare.modules.patient.entity.Patient;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.request.UpdatePatientSelfProfileRequest;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.response.PatientAppointmentHistoryItemResponse;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.response.PatientInvoiceHistoryItemResponse;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.response.PatientOverviewResponse;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.response.PatientResultHistoryItemResponse;
import com.PrimeCare.PrimeCare.modules.patient_portal.dto.response.PatientStatusHistoryItemResponse;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResultStatusHistory;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultStatusHistoryRepository;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.PatientStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PatientPortalService {

    private static final String CURRENT_PASSWORD_REQUIRED_MESSAGE = "Vui lòng nhập mật khẩu hiện tại để thay đổi email hoặc số điện thoại";
    private static final String CURRENT_PASSWORD_INCORRECT_MESSAGE = "Mật khẩu hiện tại không chính xác";

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryService appointmentStatusHistoryService;
    private final AppointmentStatusHistoryRepository appointmentStatusHistoryRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceStatusHistoryRepository invoiceStatusHistoryRepository;
    private final ServiceResultRepository serviceResultRepository;
    private final ServiceResultStatusHistoryRepository serviceResultStatusHistoryRepository;
    private final FileStorageService fileStorageService;
    private final BillingQrService billingQrService;
    private final InvoicePdfService invoicePdfService;
    private final AppointmentSelfServiceCancellationPolicy selfServiceCancellationPolicy;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PatientOverviewResponse getOverview(Long userId) {
        Patient patient = resolvePatient(userId);
        PageRequest recentRequest = PageRequest.of(0, 3);
        List<AppointmentStatus> activeUpcomingStatuses = List.of(
                AppointmentStatus.REQUESTED,
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.CHECKED_IN
        );

        List<PatientAppointmentHistoryItemResponse> recentAppointments = appointmentRepository
                .findByPatient_IdOrderByVisitDateDescCreatedAtDesc(patient.getId(), recentRequest)
                .getContent()
                .stream()
                .map(this::toAppointmentHistoryItem)
                .toList();

        List<PatientResultHistoryItemResponse> recentResults = serviceResultRepository
                .findByServiceOrderItem_ServiceOrder_Encounter_Patient_IdOrderByVerifiedAtDescCreatedAtDesc(patient.getId(), recentRequest)
                .getContent()
                .stream()
                .map(this::toResultHistoryItem)
                .toList();

        List<PatientInvoiceHistoryItemResponse> recentInvoices = invoiceRepository
                .findByServiceOrder_Encounter_Patient_IdOrderByCreatedAtDesc(patient.getId(), recentRequest)
                .getContent()
                .stream()
                .map(this::toInvoiceHistoryItem)
                .toList();

        PatientAppointmentHistoryItemResponse nextAppointment = appointmentRepository
                .findFirstByPatient_IdAndVisitDateGreaterThanEqualAndStatusInOrderByVisitDateAscEtaStartAscCreatedAtAsc(
                        patient.getId(),
                        LocalDate.now(),
                        activeUpcomingStatuses
                )
                .map(this::toAppointmentHistoryItem)
                .orElse(null);

        return PatientOverviewResponse.builder()
                .patientId(patient.getId())
                .patientCode(patient.getCode())
                .fullName(patient.getFullName())
                .upcomingAppointments(appointmentRepository.countByPatient_IdAndVisitDateGreaterThanEqualAndStatusIn(
                        patient.getId(),
                        LocalDate.now(),
                        activeUpcomingStatuses
                ))
                .totalAppointments(appointmentRepository.countByPatient_Id(patient.getId()))
                .availableResults(serviceResultRepository.countReadyByPatientId(patient.getId()))
                .totalInvoices(invoiceRepository.countByServiceOrder_Encounter_Patient_Id(patient.getId()))
                .profileCompletionPercent(calculateProfileCompletionPercent(patient))
                .summaryGeneratedAt(LocalDateTime.now().toString())
                .nextAppointment(nextAppointment)
                .recentAppointments(recentAppointments)
                .recentResults(recentResults)
                .recentInvoices(recentInvoices)
                .build();
    }

    @Transactional(readOnly = true)
    public PatientResponse getMyProfile(Long userId) {
        return toPatientResponse(resolvePatient(userId));
    }

    @Transactional
    public PatientResponse updateMyProfile(Long userId, UpdatePatientSelfProfileRequest req) {
        User user = resolveUser(userId);
        Patient patient = requirePatient(user);

        Map<String, Object> before = snapshotPatient(patient);
        String newPhone = req.getPhone() != null && !req.getPhone().isBlank() ? req.getPhone().trim() : null;
        String normalizedEmail = req.getEmail() != null ? normalize(req.getEmail()) : null;
        boolean phoneChanged = newPhone != null && !Objects.equals(newPhone, user.getPhone());
        boolean emailChanged = req.getEmail() != null && !Objects.equals(normalizedEmail, user.getEmail());
        if (phoneChanged || emailChanged) {
            verifyCurrentPassword(user, req.getCurrentPassword());
        }

        if (req.getFullName() != null && !req.getFullName().isBlank()) patient.setFullName(req.getFullName().trim());
        if (newPhone != null) {
            userRepository.findByPhone(newPhone)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> { throw new ApiException(ErrorCode.AUTH_PHONE_EXISTS); });
            patient.setPhone(newPhone);
            user.setPhone(newPhone);
        }
        if (req.getEmail() != null) {
            if (normalizedEmail != null) {
                userRepository.findByEmail(normalizedEmail)
                        .filter(existing -> !existing.getId().equals(user.getId()))
                        .ifPresent(existing -> { throw new ApiException(ErrorCode.AUTH_EMAIL_EXISTS); });
            }
            patient.setEmail(normalizedEmail);
            user.setEmail(normalizedEmail);
        }
        if (req.getAvatarUrl() != null) patient.setAvatarUrl(normalize(req.getAvatarUrl()));
        if (req.getDob() != null) patient.setDob(req.getDob());
        if (req.getGender() != null) patient.setGender(req.getGender());
        if (req.getAddress() != null) patient.setAddress(normalize(req.getAddress()));
        if (req.getInsuranceNumber() != null) patient.setInsuranceNumber(normalize(req.getInsuranceNumber()));
        if (req.getEmergencyContactName() != null) patient.setEmergencyContactName(normalize(req.getEmergencyContactName()));
        if (req.getEmergencyContactPhone() != null) patient.setEmergencyContactPhone(normalize(req.getEmergencyContactPhone()));
        if (req.getAllergyNote() != null) patient.setAllergyNote(normalize(req.getAllergyNote()));
        if (req.getChronicDiseaseNote() != null) patient.setChronicDiseaseNote(normalize(req.getChronicDiseaseNote()));
        if (req.getNote() != null) patient.setNote(normalize(req.getNote()));
        auditLogService.log(user, "UPDATE_PROFILE", "PATIENT", patient.getId(), before, snapshotPatient(patient));
        return toPatientResponse(patient);
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientAppointmentHistoryItemResponse> listMyAppointments(Long userId, Pageable pageable) {
        Patient patient = resolvePatient(userId);
        Page<Appointment> page = appointmentRepository.findByPatient_IdOrderByVisitDateDescCreatedAtDesc(patient.getId(), pageable);
        return PageResponse.<PatientAppointmentHistoryItemResponse>builder()
                .items(page.getContent().stream().map(this::toAppointmentHistoryItem).toList())
                .meta(PageResponse.Meta.builder()
                        .page(page.getNumber())
                        .size(page.getSize())
                        .totalItems(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .hasNext(page.hasNext())
                        .hasPrev(page.hasPrevious())
                        .sort(pageable.getSort().toString())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PatientStatusHistoryItemResponse> getAppointmentStatusHistory(Long userId, Long appointmentId) {
        Patient patient = resolvePatient(userId);
        Appointment appointment = resolveAppointmentForPatient(patient.getId(), appointmentId);
        return appointmentStatusHistoryRepository.findByAppointment_IdOrderByChangedAtDesc(appointment.getId())
                .stream()
                .map(this::toAppointmentStatusHistoryItem)
                .toList();
    }

    @Transactional
    public PatientAppointmentHistoryItemResponse cancelMyAppointment(Long userId, Long appointmentId, String reason) {
        User user = resolveUser(userId);
        Patient patient = requirePatient(user);
        Appointment appointment = resolveAppointmentForPatient(patient.getId(), appointmentId);

        selfServiceCancellationPolicy.assertCanCancel(appointment);

        AppointmentStatus previousStatus = appointment.getStatus();
        selfServiceCancellationPolicy.applyCancellation(
                appointment,
                user,
                reason,
                "Patient portal self-service cancellation"
        );

        Appointment saved = appointmentRepository.save(appointment);
        appointmentStatusHistoryService.record(saved, previousStatus, saved.getStatus(), user, saved.getCancelReason());
        return toAppointmentHistoryItem(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientInvoiceHistoryItemResponse> listMyInvoices(Long userId, Pageable pageable) {
        Patient patient = resolvePatient(userId);
        Page<Invoice> page = invoiceRepository.findByServiceOrder_Encounter_Patient_IdOrderByCreatedAtDesc(patient.getId(), pageable);
        return PageResponse.<PatientInvoiceHistoryItemResponse>builder()
                .items(page.getContent().stream().map(this::toInvoiceHistoryItem).toList())
                .meta(PageResponse.Meta.builder()
                        .page(page.getNumber())
                        .size(page.getSize())
                        .totalItems(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .hasNext(page.hasNext())
                        .hasPrev(page.hasPrevious())
                        .sort(pageable.getSort().toString())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PatientStatusHistoryItemResponse> getInvoiceStatusHistory(Long userId, Long invoiceId) {
        Patient patient = resolvePatient(userId);
        Invoice invoice = resolveInvoiceForPatient(patient.getId(), invoiceId);
        return invoiceStatusHistoryRepository.findByInvoice_IdOrderByChangedAtDesc(invoice.getId())
                .stream()
                .map(this::toInvoiceStatusHistoryItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] downloadMyInvoicePdf(Long userId, Long invoiceId) {
        Patient patient = resolvePatient(userId);
        Invoice invoice = resolveInvoiceForPatient(patient.getId(), invoiceId);
        byte[] qrPng = billingQrService.generatePaymentQr(invoice);
        return invoicePdfService.generate(
                invoice,
                qrPng,
                billingQrService.bankCode(),
                billingQrService.accountNo(),
                billingQrService.accountName(),
                billingQrService.buildPaymentContent(invoice)
        );
    }

    @Transactional(readOnly = true)
    public String buildInvoicePdfFileName(Long userId, Long invoiceId) {
        Patient patient = resolvePatient(userId);
        Invoice invoice = resolveInvoiceForPatient(patient.getId(), invoiceId);
        return "invoice-" + (invoice.getCode() != null ? invoice.getCode() : invoice.getId()) + ".pdf";
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientResultHistoryItemResponse> listMyResults(Long userId, Pageable pageable) {
        Patient patient = resolvePatient(userId);
        Page<ServiceResult> page = serviceResultRepository.findByServiceOrderItem_ServiceOrder_Encounter_Patient_IdOrderByVerifiedAtDescCreatedAtDesc(patient.getId(), pageable);
        return PageResponse.<PatientResultHistoryItemResponse>builder()
                .items(page.getContent().stream().map(this::toResultHistoryItem).toList())
                .meta(PageResponse.Meta.builder()
                        .page(page.getNumber())
                        .size(page.getSize())
                        .totalItems(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .hasNext(page.hasNext())
                        .hasPrev(page.hasPrevious())
                        .sort(pageable.getSort().toString())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PatientStatusHistoryItemResponse> getResultStatusHistory(Long userId, Long resultId) {
        Patient patient = resolvePatient(userId);
        ServiceResult result = resolveResultForPatient(patient.getId(), resultId);
        return serviceResultStatusHistoryRepository.findByServiceResult_IdOrderByChangedAtDesc(result.getId())
                .stream()
                .map(this::toResultStatusHistoryItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] downloadMyResultPdf(Long userId, Long resultId) {
        Patient patient = resolvePatient(userId);
        ServiceResult result = resolveResultForPatient(patient.getId(), resultId);
        if (result.getReportPdfPath() == null || result.getReportPdfPath().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "PDF kết quả chưa sẵn sàng để xem");
        }
        return fileStorageService.downloadAsBytes(result.getReportPdfPath());
    }

    @Transactional(readOnly = true)
    public String buildResultPdfFileName(Long userId, Long resultId) {
        Patient patient = resolvePatient(userId);
        ServiceResult result = resolveResultForPatient(patient.getId(), resultId);
        String suffix = result.getServiceOrderItem() != null && result.getServiceOrderItem().getId() != null
                ? result.getServiceOrderItem().getId().toString()
                : result.getId().toString();
        return "service-result-" + suffix + ".pdf";
    }

    private User resolveUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private Patient resolvePatient(Long userId) {
        return requirePatient(resolveUser(userId));
    }

    private Patient requirePatient(User user) {
        if (user.getRole() != UserRole.PATIENT) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        Patient patient = user.getPatient();
        if (patient == null) throw new ApiException(ErrorCode.PATIENT_ACCOUNT_NOT_LINKED);
        if (patient.getStatus() == null) patient.setStatus(PatientStatus.ACTIVE);
        return patient;
    }

    private void verifyCurrentPassword(User user, String currentPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw currentPasswordValidationError(CURRENT_PASSWORD_REQUIRED_MESSAGE);
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw currentPasswordValidationError(CURRENT_PASSWORD_INCORRECT_MESSAGE);
        }
    }

    private ApiException currentPasswordValidationError(String message) {
        return new ApiException(
                ErrorCode.VALIDATION_ERROR,
                message,
                Map.of("fields", Map.of("currentPassword", message))
        );
    }

    private Appointment resolveAppointmentForPatient(Long patientId, Long appointmentId) {
        return appointmentRepository.findByIdAndPatient_Id(appointmentId, patientId)
                .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND));
    }

    private Invoice resolveInvoiceForPatient(Long patientId, Long invoiceId) {
        return invoiceRepository.findByIdAndServiceOrder_Encounter_Patient_Id(invoiceId, patientId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVOICE_NOT_FOUND));
    }

    private ServiceResult resolveResultForPatient(Long patientId, Long resultId) {
        return serviceResultRepository.findByIdAndServiceOrderItem_ServiceOrder_Encounter_Patient_Id(resultId, patientId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST, "Không tìm thấy kết quả phù hợp"));
    }

    private PatientAppointmentHistoryItemResponse toAppointmentHistoryItem(Appointment appointment) {
        String cancelBlockedReason = selfServiceCancellationPolicy.getBlockedReason(appointment);
        return PatientAppointmentHistoryItemResponse.builder()
                .id(appointment.getId())
                .code(appointment.getCode())
                .status(appointment.getStatus() != null ? appointment.getStatus().name() : null)
                .visitDate(appointment.getVisitDate() != null ? appointment.getVisitDate().toString() : null)
                .session(appointment.getSession() != null ? appointment.getSession().name() : null)
                .branchName(appointment.getBranch() != null ? appointment.getBranch().getNameVn() : null)
                .specialtyName(appointment.getSpecialty() != null ? appointment.getSpecialty().getNameVn() : null)
                .doctorName(appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : null)
                .etaStart(appointment.getEtaStart() != null ? appointment.getEtaStart().toString() : null)
                .etaEnd(appointment.getEtaEnd() != null ? appointment.getEtaEnd().toString() : null)
                .createdAt(appointment.getCreatedAt() != null ? appointment.getCreatedAt().toString() : null)
                .canCancel(cancelBlockedReason == null)
                .cancelBlockedReason(cancelBlockedReason)
                .build();
    }

    private PatientInvoiceHistoryItemResponse toInvoiceHistoryItem(Invoice invoice) {
        return PatientInvoiceHistoryItemResponse.builder()
                .id(invoice.getId())
                .code(invoice.getCode())
                .serviceOrderCode(invoice.getServiceOrder() != null ? invoice.getServiceOrder().getCode() : null)
                .totalAmount(invoice.getTotalAmount())
                .paymentStatus(invoice.getPaymentStatus() != null ? invoice.getPaymentStatus().name() : null)
                .paymentMethod(invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : null)
                .paidAt(invoice.getPaidAt() != null ? invoice.getPaidAt().toString() : null)
                .createdAt(invoice.getCreatedAt() != null ? invoice.getCreatedAt().toString() : null)
                .canDownloadPdf(true)
                .build();
    }

    private PatientResultHistoryItemResponse toResultHistoryItem(ServiceResult result) {
        return PatientResultHistoryItemResponse.builder()
                .resultId(result.getId())
                .serviceOrderItemId(result.getServiceOrderItem() != null ? result.getServiceOrderItem().getId() : null)
                .serviceName(result.getServiceOrderItem() != null ? result.getServiceOrderItem().getServiceNameVnSnapshot() : null)
                .encounterCode(result.getServiceOrderItem() != null && result.getServiceOrderItem().getServiceOrder() != null && result.getServiceOrderItem().getServiceOrder().getEncounter() != null ? result.getServiceOrderItem().getServiceOrder().getEncounter().getCode() : null)
                .status(result.getStatus() != null ? result.getStatus().name() : null)
                .reportPdfStatus(result.getReportPdfStatus() != null ? result.getReportPdfStatus().name() : null)
                .pdfReady(result.getReportPdfPath() != null && !result.getReportPdfPath().isBlank())
                .verifiedAt(result.getVerifiedAt() != null ? result.getVerifiedAt().toString() : null)
                .performedAt(result.getPerformedAt() != null ? result.getPerformedAt().toString() : null)
                .build();
    }

    private PatientStatusHistoryItemResponse toAppointmentStatusHistoryItem(AppointmentStatusHistory history) {
        return PatientStatusHistoryItemResponse.builder()
                .id(history.getId())
                .fromStatus(history.getFromStatus() != null ? history.getFromStatus().name() : null)
                .toStatus(history.getToStatus() != null ? history.getToStatus().name() : null)
                .changedAt(history.getChangedAt() != null ? history.getChangedAt().toString() : null)
                .changedBy(resolveUserDisplayName(history.getChangedBy()))
                .note(history.getNote())
                .build();
    }

    private PatientStatusHistoryItemResponse toInvoiceStatusHistoryItem(InvoiceStatusHistory history) {
        return PatientStatusHistoryItemResponse.builder()
                .id(history.getId())
                .fromStatus(history.getFromStatus() != null ? history.getFromStatus().name() : null)
                .toStatus(history.getToStatus() != null ? history.getToStatus().name() : null)
                .changedAt(history.getChangedAt() != null ? history.getChangedAt().toString() : null)
                .changedBy(resolveUserDisplayName(history.getChangedBy()))
                .note(history.getNote())
                .build();
    }

    private PatientStatusHistoryItemResponse toResultStatusHistoryItem(ServiceResultStatusHistory history) {
        return PatientStatusHistoryItemResponse.builder()
                .id(history.getId())
                .fromStatus(history.getFromStatus() != null ? history.getFromStatus().name() : null)
                .toStatus(history.getToStatus() != null ? history.getToStatus().name() : null)
                .changedAt(history.getChangedAt() != null ? history.getChangedAt().toString() : null)
                .changedBy(resolveUserDisplayName(history.getChangedBy()))
                .note(history.getNote())
                .build();
    }

    private PatientResponse toPatientResponse(Patient patient) {
        return PatientResponse.builder()
                .id(patient.getId())
                .code(patient.getCode())
                .avatarUrl(patient.getAvatarUrl())
                .fullName(patient.getFullName())
                .phone(patient.getPhone())
                .email(patient.getEmail())
                .dob(patient.getDob())
                .gender(patient.getGender())
                .address(patient.getAddress())
                .identityNumber(patient.getIdentityNumber())
                .insuranceNumber(patient.getInsuranceNumber())
                .emergencyContactName(patient.getEmergencyContactName())
                .emergencyContactPhone(patient.getEmergencyContactPhone())
                .allergyNote(patient.getAllergyNote())
                .chronicDiseaseNote(patient.getChronicDiseaseNote())
                .note(patient.getNote())
                .status(patient.getStatus())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .build();
    }

    private Integer calculateProfileCompletionPercent(Patient patient) {
        int total = 10;
        int completed = 0;
        if (hasText(patient.getFullName())) completed++;
        if (hasText(patient.getPhone())) completed++;
        if (hasText(patient.getEmail())) completed++;
        if (patient.getDob() != null) completed++;
        if (patient.getGender() != null) completed++;
        if (hasText(patient.getAddress())) completed++;
        if (hasText(patient.getInsuranceNumber())) completed++;
        if (hasText(patient.getEmergencyContactName())) completed++;
        if (hasText(patient.getEmergencyContactPhone())) completed++;
        if (hasText(patient.getAvatarUrl())) completed++;
        return Math.round((completed * 100f) / total);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, Object> snapshotPatient(Patient patient) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", patient.getId());
        data.put("code", patient.getCode());
        data.put("fullName", patient.getFullName());
        data.put("phone", patient.getPhone());
        data.put("email", patient.getEmail());
        data.put("dob", patient.getDob());
        data.put("gender", patient.getGender() != null ? patient.getGender().name() : null);
        data.put("address", patient.getAddress());
        data.put("insuranceNumber", patient.getInsuranceNumber());
        data.put("emergencyContactName", patient.getEmergencyContactName());
        data.put("emergencyContactPhone", patient.getEmergencyContactPhone());
        data.put("allergyNote", patient.getAllergyNote());
        data.put("chronicDiseaseNote", patient.getChronicDiseaseNote());
        data.put("note", patient.getNote());
        data.put("status", patient.getStatus() != null ? patient.getStatus().name() : null);
        data.put("updatedAt", patient.getUpdatedAt());
        return data;
    }

    private String resolveUserDisplayName(User user) {
        if (user == null) return "Hệ thống";
        if (user.getPatient() != null && user.getPatient().getFullName() != null && !user.getPatient().getFullName().isBlank()) {
            return user.getPatient().getFullName();
        }
        if (user.getStaffProfile() != null && user.getStaffProfile().getFullName() != null && !user.getStaffProfile().getFullName().isBlank()) {
            return user.getStaffProfile().getFullName();
        }
        if (user.getDoctorProfile() != null && user.getDoctorProfile().getFullName() != null && !user.getDoctorProfile().getFullName().isBlank()) {
            return user.getDoctorProfile().getFullName();
        }
        return user.getEmail();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
