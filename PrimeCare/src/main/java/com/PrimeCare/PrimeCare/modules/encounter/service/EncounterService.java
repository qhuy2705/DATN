package com.PrimeCare.PrimeCare.modules.encounter.service;

import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.booking_restriction.service.PatientViolationEventService;
import com.PrimeCare.PrimeCare.modules.encounter.dto.request.UpdateEncounterRequest;
import com.PrimeCare.PrimeCare.modules.encounter.dto.record.EncounterWorkflowState;
import com.PrimeCare.PrimeCare.modules.encounter.dto.response.EncounterDiagnosisResponse;
import com.PrimeCare.PrimeCare.modules.encounter.dto.response.EncounterResponse;
import com.PrimeCare.PrimeCare.modules.encounter.dto.response.EncounterTimelineItemResponse;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.entity.EncounterDiagnosis;
import com.PrimeCare.PrimeCare.modules.encounter.entity.EncounterReopenLog;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterDiagnosisRepository;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterReopenLogRepository;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.notification.messaging.event.ResultReadyMailEvent;
import com.PrimeCare.PrimeCare.modules.notification.messaging.AppointmentMailEventPublisher;
import com.PrimeCare.PrimeCare.modules.prescription.entity.Prescription;
import com.PrimeCare.PrimeCare.modules.prescription.repository.PrescriptionRepository;
import com.PrimeCare.PrimeCare.modules.realtime.service.AfterCommitExecutor;
import com.PrimeCare.PrimeCare.modules.realtime.service.RealtimeEventPublisher;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrder;
import com.PrimeCare.PrimeCare.modules.service_order.entity.ServiceOrderItem;
import com.PrimeCare.PrimeCare.modules.service_order.repository.ServiceOrderRepository;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.repository.ServiceResultRepository;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.enums.EncounterStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.PrimeCare.PrimeCare.shared.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EncounterService {

    private final EncounterRepository encounterRepository;
    private final EncounterDiagnosisRepository encounterDiagnosisRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ServiceResultRepository serviceResultRepository;
    private final EncounterWorkflowService encounterWorkflowService;
    private final EncounterReopenLogRepository encounterReopenLogRepository;
    private final AppointmentMailEventPublisher appointmentMailEventPublisher;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final AfterCommitExecutor afterCommitExecutor;
    private final AuditLogService auditLogService;
    private final PatientViolationEventService patientViolationEventService;

    @Value("${app.encounter.max-reopen-count:3}")
    private int maxReopenCount;

    @Transactional
    public EncounterResponse createFromAppointment(Long appointmentId, Long doctorUserId) {
        User doctorUser = getDoctorUser(doctorUserId);

        var existingEncounter = encounterRepository.findByAppointment_Id(appointmentId);
        if (existingEncounter.isPresent()) {
            Encounter existing = existingEncounter.get();
            if (existing.getDoctor() != null && doctorUser.getDoctorProfile() != null
                    && java.util.Objects.equals(existing.getDoctor().getId(), doctorUser.getDoctorProfile().getId())) {
                return toResponse(existing);
            }
            throw new ApiException(ErrorCode.ENCOUNTER_ALREADY_EXISTS);
        }

        Appointment appointment = appointmentRepository.findByIdAndDoctor_Id(appointmentId, doctorUser.getDoctorProfile().getId())
                                                       .orElseThrow(() -> new ApiException(ErrorCode.APPOINTMENT_NOT_FOUND));

        if (appointment.getStatus() != AppointmentStatus.CHECKED_IN) {
            throw new ApiException(
                    ErrorCode.APPOINTMENT_INVALID_STATUS,
                    "Chỉ lịch đã check-in mới được mở lần khám"
            );
        }

        Encounter encounter = Encounter.builder()
                                       .code(generateCode(appointment))
                                       .appointment(appointment)
                                       .branch(appointment.getBranch())
                                       .specialty(appointment.getSpecialty())
                                       .doctor(appointment.getDoctor())
                                       .patient(appointment.getPatient())
                                       .patientFullNameSnapshot(appointment.getPatientFullName())
                                       .patientPhoneSnapshot(appointment.getPatientPhone())
                                       .patientEmailSnapshot(appointment.getPatientEmail())
                                       .patientDobSnapshot(appointment.getPatientDob())
                                       .patientGenderSnapshot(appointment.getPatientGender())
                                       .intakeReasonForVisit(appointment.getReasonForVisit())
                                       .visitType(appointment.getVisitType())
                                       .triagePriority(appointment.getTriagePriority())
                                       .triageNote(appointment.getTriageNote())
                                       .insuranceNote(appointment.getInsuranceNote())
                                       .emergencyContactName(appointment.getEmergencyContactName())
                                       .emergencyContactPhone(appointment.getEmergencyContactPhone())
                                       .intakeCompletedAt(appointment.getIntakeCompletedAt())
                                       .intakeCompletedByName(
                                               appointment.getIntakeCompletedBy() != null
                                                       ? resolveUserDisplayName(appointment.getIntakeCompletedBy())
                                                       : null
                                       )
                                       .chiefComplaint(normalize(appointment.getReasonForVisit()))
                                       .heightCm(appointment.getHeightCm())
                                       .weightKg(appointment.getWeightKg())
                                       .temperatureC(appointment.getTemperatureC())
                                       .pulse(appointment.getPulse())
                                       .systolicBp(appointment.getSystolicBp())
                                       .diastolicBp(appointment.getDiastolicBp())
                                       .respiratoryRate(appointment.getRespiratoryRate())
                                       .spo2(appointment.getSpo2())
                                       .allergySnapshot(
                                               appointment.getPatient() != null
                                                       ? appointment.getPatient().getAllergyNote()
                                                       : null
                                       )
                                       .chronicDiseaseSnapshot(
                                               appointment.getPatient() != null
                                                       ? appointment.getPatient().getChronicDiseaseNote()
                                                       : null
                                       )
                                       .status(EncounterStatus.IN_PROGRESS)
                                       .startedAt(LocalDateTime.now())
                                       .build();

        try {
            Encounter saved = encounterRepository.saveAndFlush(encounter);
            afterCommitExecutor.execute(() -> {
                publishEncounterRealtime(saved, "ENCOUNTER_CREATED");
                publishAppointmentRealtime(saved.getAppointment(), saved.getAppointment().getStatus());
            });
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.ENCOUNTER_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public EncounterResponse get(Long id, Long doctorUserId) {
        return toResponse(getEncounterForDoctor(id, doctorUserId));
    }


    @Transactional(readOnly = true)
    public List<EncounterTimelineItemResponse> getTimeline(Long encounterId, Long doctorUserId) {
        Encounter encounter = getEncounterForDoctor(encounterId, doctorUserId);
        List<EncounterTimelineItemResponse> items = new ArrayList<>();

        Appointment appointment = encounter.getAppointment();
        if (appointment != null) {
            items.add(timelineItem(
                    "appointment-created-" + appointment.getId(),
                    "APPOINTMENT_CREATED",
                    "BOOKING",
                    "Lịch khám được tạo",
                    buildAppointmentSummary(appointment),
                    appointment.getCreatedAt(),
                    null,
                    appointment.getStatus() != null ? appointment.getStatus().name() : null,
                    appointment.getCode()
            ));

            if (appointment.getConfirmedAt() != null) {
                items.add(timelineItem(
                        "appointment-confirmed-" + appointment.getId(),
                        "APPOINTMENT_CONFIRMED",
                        "BOOKING",
                        "Lịch khám đã xác nhận",
                        appointment.getDoctor() != null ? "Bác sĩ: " + appointment.getDoctor().getFullName() : null,
                        appointment.getConfirmedAt(),
                        appointment.getConfirmedBy() != null ? resolveUserDisplayName(appointment.getConfirmedBy()) : null,
                        appointment.getStatus() != null ? appointment.getStatus().name() : null,
                        appointment.getCode()
                ));
            }

            if (appointment.getIntakeCompletedAt() != null) {
                items.add(timelineItem(
                        "appointment-intake-" + appointment.getId(),
                        "INTAKE_COMPLETED",
                        "RECEPTION",
                        "Tiếp đón / sàng lọc ban đầu hoàn tất",
                        firstNonBlank(appointment.getReasonForVisit(), appointment.getTriageNote(), appointment.getInsuranceNote()),
                        appointment.getIntakeCompletedAt(),
                        appointment.getIntakeCompletedBy() != null ? resolveUserDisplayName(appointment.getIntakeCompletedBy()) : null,
                        appointment.getTriagePriority(),
                        appointment.getCode()
                ));
            }

            if (appointment.getArrivedAt() != null) {
                items.add(timelineItem(
                        "appointment-arrived-" + appointment.getId(),
                        "PATIENT_ARRIVED",
                        "RECEPTION",
                        "Người bệnh đã tới quầy",
                        appointment.getBranch() != null ? firstNonBlank(appointment.getBranch().getNameVn(), appointment.getBranch().getNameEn()) : null,
                        appointment.getArrivedAt(),
                        appointment.getArrivedBy() != null ? resolveUserDisplayName(appointment.getArrivedBy()) : null,
                        appointment.getArrivalStatus() != null ? appointment.getArrivalStatus().name() : null,
                        appointment.getCode()
                ));
            }

            if (appointment.getCheckedInAt() != null) {
                items.add(timelineItem(
                        "appointment-checked-in-" + appointment.getId(),
                        "CHECKED_IN",
                        "RECEPTION",
                        "Đã check-in vào phòng khám",
                        appointment.getReceptionQueueNo() != null ? "Số tiếp nhận: " + appointment.getReceptionQueueNo() : null,
                        appointment.getCheckedInAt(),
                        appointment.getCheckedInBy() != null ? resolveUserDisplayName(appointment.getCheckedInBy()) : null,
                        appointment.getStatus() != null ? appointment.getStatus().name() : null,
                        appointment.getCode()
                ));
            }
        }

        items.add(timelineItem(
                "encounter-started-" + encounter.getId(),
                "ENCOUNTER_STARTED",
                "CLINICAL",
                "Bác sĩ mở lần khám",
                firstNonBlank(encounter.getChiefComplaint(), encounter.getIntakeReasonForVisit()),
                firstNonBlank(encounter.getStartedAt(), encounter.getCreatedAt()),
                encounter.getDoctor() != null ? encounter.getDoctor().getFullName() : null,
                encounter.getStatus() != null ? encounter.getStatus().name() : null,
                encounter.getCode()
        ));

        List<ServiceOrder> orders = serviceOrderRepository.findByEncounter_IdOrderByCreatedAtDesc(encounter.getId());
        for (ServiceOrder order : orders) {
            items.add(timelineItem(
                    "service-order-" + order.getId(),
                    "SERVICE_ORDER_CREATED",
                    order.getPaymentStatus() == com.PrimeCare.PrimeCare.shared.enums.PaymentStatus.PAID ? "BILLING" : "DIAGNOSTICS",
                    "Tạo chỉ định dịch vụ",
                    order.getItems().isEmpty() ? order.getNote() : "Số dịch vụ: " + order.getItems().size(),
                    firstNonBlank(order.getOrderedAt(), order.getCreatedAt()),
                    order.getOrderedByDoctor() != null ? resolveUserDisplayName(order.getOrderedByDoctor()) : null,
                    order.getStatus() != null ? order.getStatus().name() : null,
                    order.getCode()
            ));

            if (order.getPaidAt() != null) {
                items.add(timelineItem(
                        "service-order-paid-" + order.getId(),
                        "SERVICE_ORDER_PAID",
                        "BILLING",
                        "Chỉ định đã thanh toán",
                        order.getEstimatedTotalAmount() != null ? "Tạm thu: " + order.getEstimatedTotalAmount() + " VND" : null,
                        order.getPaidAt(),
                        null,
                        order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null,
                        order.getCode()
                ));
            }

            for (ServiceOrderItem item : order.getItems()) {
                if (item.getStartedAt() != null) {
                    items.add(timelineItem(
                            "service-item-started-" + item.getId(),
                            "SERVICE_ITEM_STARTED",
                            "DIAGNOSTICS",
                            "Bắt đầu thực hiện cận lâm sàng",
                            firstNonBlank(item.getServiceNameVnSnapshot(), item.getServiceNameEnSnapshot(), item.getServiceCodeSnapshot()),
                            item.getStartedAt(),
                            null,
                            item.getStatus() != null ? item.getStatus().name() : null,
                            order.getCode()
                    ));
                }
            }
        }

        List<ServiceResult> results = serviceResultRepository.findByServiceOrderItem_ServiceOrder_Encounter_Id(encounter.getId());
        for (ServiceResult result : results) {
            ServiceOrderItem item = result.getServiceOrderItem();
            if (result.getPerformedAt() != null || result.getCreatedAt() != null) {
                items.add(timelineItem(
                        "service-result-performed-" + result.getId(),
                        "SERVICE_RESULT_COMPLETED",
                        "DIAGNOSTICS",
                        "Đã có kết quả cận lâm sàng",
                        firstNonBlank(result.getReportTitle(), item != null ? item.getServiceNameVnSnapshot() : null, item != null ? item.getServiceCodeSnapshot() : null),
                        firstNonBlank(result.getPerformedAt(), result.getCreatedAt()),
                        result.getPerformedByUser() != null ? resolveUserDisplayName(result.getPerformedByUser()) : null,
                        result.getStatus() != null ? result.getStatus().name() : null,
                        item != null && item.getServiceOrder() != null ? item.getServiceOrder().getCode() : null
                ));
            }
            if (result.getVerifiedAt() != null) {
                items.add(timelineItem(
                        "service-result-verified-" + result.getId(),
                        "SERVICE_RESULT_VERIFIED",
                        "DIAGNOSTICS",
                        "Kết quả đã được xác minh",
                        firstNonBlank(result.getReportTitle(), item != null ? item.getServiceNameVnSnapshot() : null),
                        result.getVerifiedAt(),
                        result.getVerifiedByUser() != null ? resolveUserDisplayName(result.getVerifiedByUser()) : null,
                        result.getStatus() != null ? result.getStatus().name() : null,
                        item != null && item.getServiceOrder() != null ? item.getServiceOrder().getCode() : null
                ));
            }
        }

        var prescriptionsPage = prescriptionRepository.findByEncounter_IdOrderByCreatedAtDesc(
                encounter.getId(),
                org.springframework.data.domain.PageRequest.of(0, 20)
        );
        for (Prescription prescription : prescriptionsPage.getContent()) {
            items.add(timelineItem(
                    "prescription-issued-" + prescription.getId(),
                    "PRESCRIPTION_ISSUED",
                    "PRESCRIPTION",
                    "Đã tạo đơn thuốc",
                    prescription.getItems() != null ? "Số thuốc: " + prescription.getItems().size() : null,
                    prescription.getCreatedAt() != null ? prescription.getCreatedAt() : (prescription.getIssuedDate() != null ? prescription.getIssuedDate().atStartOfDay() : null),
                    prescription.getDoctorUser() != null ? resolveUserDisplayName(prescription.getDoctorUser()) : null,
                    prescription.getStatus() != null ? prescription.getStatus().name() : null,
                    prescription.getCode()
            ));
        }

        if (encounter.getCompletedAt() != null) {
            items.add(timelineItem(
                    "encounter-completed-" + encounter.getId(),
                    "ENCOUNTER_COMPLETED",
                    "COMPLETION",
                    "Lần khám hoàn tất",
                    firstNonBlank(encounter.getFinalDiagnosis(), encounter.getConclusion()),
                    encounter.getCompletedAt(),
                    encounter.getDoctor() != null ? encounter.getDoctor().getFullName() : null,
                    encounter.getStatus() != null ? encounter.getStatus().name() : null,
                    encounter.getCode()
            ));
        }

        items.sort(Comparator.comparing(EncounterTimelineItemResponse::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    @Transactional
    public EncounterResponse update(Long id, Long doctorUserId, UpdateEncounterRequest req) {
        User doctorUser = getDoctorUser(doctorUserId);
        Encounter e = getEncounterForDoctor(id, doctorUser);

        if (e.getStatus() == EncounterStatus.COMPLETED || e.getStatus() == EncounterStatus.CANCELLED) {
            throw new ApiException(ErrorCode.ENCOUNTER_INVALID_STATUS);
        }

        Map<String, Object> before = snapshotEncounter(e);
        applyClinicalData(e, req);
        Encounter saved = encounterRepository.save(e);
        auditLogService.log(doctorUser, "UPDATE", "ENCOUNTER", saved.getId(), before, snapshotEncounter(saved));
        afterCommitExecutor.execute(() -> publishEncounterRealtime(saved, "ENCOUNTER_UPDATED"));
        return toResponse(saved);
    }

    @Transactional
    public EncounterResponse complete(Long id, Long doctorUserId, UpdateEncounterRequest req) {
        User doctorUser = getDoctorUser(doctorUserId);
        Encounter e = encounterRepository.findWithLockById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.ENCOUNTER_NOT_FOUND));
        assertEncounterBelongsToDoctor(e, doctorUser);

        if (e.getStatus() == EncounterStatus.COMPLETED) {
            return toResponse(e);
        }

        if (e.getStatus() == EncounterStatus.CANCELLED) {
            throw new ApiException(ErrorCode.ENCOUNTER_INVALID_STATUS);
        }

        applyClinicalData(e, req);
        EncounterWorkflowState workflowState = validateCompletionRules(e);

        Appointment appointment = e.getAppointment();
        AppointmentStatus previousAppointmentStatus = appointment != null ? appointment.getStatus() : null;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime completedAt = e.getCompletedAt() != null ? e.getCompletedAt() : now;

        e.setStatus(EncounterStatus.COMPLETED);
        e.setCompletedAt(completedAt);
        if (appointment != null) {
            appointment.setStatus(AppointmentStatus.COMPLETED);
            if (appointment.getCompletedAt() == null) {
                appointment.setCompletedAt(completedAt);
            }
        }

        Encounter saved = encounterRepository.save(e);
        if (saved.getAppointment() != null) {
            patientViolationEventService.recordSuccessfulVisitCredit(saved.getAppointment(), doctorUser);
        }
        EncounterWorkflowState completedState = encounterWorkflowService.getWorkflowState(saved);
        afterCommitExecutor.execute(() -> {
            publishEncounterRealtime(saved, "ENCOUNTER_COMPLETED");
            publishAppointmentRealtime(saved.getAppointment(), previousAppointmentStatus);
            if (saved.getAppointment() != null
                    && saved.getAppointment().getPatientEmail() != null
                    && !saved.getAppointment().getPatientEmail().isBlank()
                    && workflowState.completedResultItemCount() > 0) {
                appointmentMailEventPublisher.publishResultReady(new ResultReadyMailEvent(
                        saved.getAppointment().getId(),
                        saved.getId(),
                        saved.getCode(),
                        saved.getAppointment().getCode(),
                        saved.getPatientFullNameSnapshot(),
                        saved.getAppointment().getPatientEmail(),
                        saved.getDoctor() != null ? saved.getDoctor().getFullName() : null,
                        saved.getBranch() != null ? saved.getBranch().getNameVn() : null
                ));
            }
        });

        return toResponse(saved, completedState);
    }

    private EncounterWorkflowState validateCompletionRules(Encounter encounter) {
        EncounterWorkflowState workflowState = encounterWorkflowService.getWorkflowState(encounter);

        if (workflowState.hasPendingPayment()) {
            throw new ApiException(ErrorCode.ENCOUNTER_INVALID_STATUS, "Lần khám đang có chỉ định chưa thanh toán");
        }

        if (workflowState.hasWaitingResults()) {
            throw new ApiException(ErrorCode.ENCOUNTER_INVALID_STATUS, "Lần khám đang chờ kết quả dịch vụ cận lâm sàng");
        }

        if (normalize(encounter.getFinalDiagnosis()) == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Cần nhập chẩn đoán cuối cùng trước khi kết thúc lần khám");
        }

        if (normalize(encounter.getConclusion()) == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Cần nhập kết luận trước khi kết thúc lần khám");
        }

        return workflowState;
    }

    private User getDoctorUser(Long doctorUserId) {
        User doctorUser = userRepository.findById(doctorUserId)
                                        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        if (doctorUser.getDoctorProfile() == null || doctorUser.getDoctorProfile().getId() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Bạn không thuộc bác sĩ phụ trách lần khám này");
        }

        return doctorUser;
    }

    private Encounter getEncounterForDoctor(Long encounterId, Long doctorUserId) {
        return getEncounterForDoctor(encounterId, getDoctorUser(doctorUserId));
    }

    private Encounter getEncounterForDoctor(Long encounterId, User doctorUser) {
        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ApiException(ErrorCode.ENCOUNTER_NOT_FOUND));
        assertEncounterBelongsToDoctor(encounter, doctorUser);
        return encounter;
    }

    private void assertEncounterBelongsToDoctor(Encounter encounter, User doctorUser) {
        Long requesterDoctorId = doctorUser.getDoctorProfile() != null ? doctorUser.getDoctorProfile().getId() : null;
        Long encounterDoctorId = encounter.getDoctor() != null ? encounter.getDoctor().getId() : null;
        if (requesterDoctorId == null || encounterDoctorId == null || !requesterDoctorId.equals(encounterDoctorId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Bác sĩ chỉ được truy cập hồ sơ khám do mình phụ trách");
        }
    }


    private EncounterTimelineItemResponse timelineItem(
            String id,
            String eventType,
            String stage,
            String title,
            String description,
            LocalDateTime occurredAt,
            String actorName,
            String status,
            String referenceCode
    ) {
        return EncounterTimelineItemResponse.builder()
                .id(id)
                .eventType(eventType)
                .stage(stage)
                .title(title)
                .description(description)
                .occurredAt(occurredAt)
                .actorName(actorName)
                .status(status)
                .referenceCode(referenceCode)
                .build();
    }

    private String buildAppointmentSummary(Appointment appointment) {
        List<String> parts = new ArrayList<>();
        if (appointment.getVisitDate() != null) {
            parts.add("Ngày khám: " + appointment.getVisitDate());
        }
        if (appointment.getDoctor() != null && appointment.getDoctor().getFullName() != null) {
            parts.add("Bác sĩ: " + appointment.getDoctor().getFullName());
        }
        if (appointment.getSpecialty() != null) {
            parts.add("Chuyên khoa: " + firstNonBlank(appointment.getSpecialty().getNameVn(), appointment.getSpecialty().getNameEn()));
        }
        return String.join(" · ", parts);
    }

    private LocalDateTime firstNonBlank(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }

    private void publishEncounterRealtime(Encounter encounter, String encounterEvent) {
        if (encounter.getDoctor() != null && encounter.getDoctor().getId() != null) {
            realtimeEventPublisher.publishDoctorEncounterUpdated(
                    encounter.getDoctor().getId(),
                    encounter.getId(),
                    encounter.getStatus() != null ? encounter.getStatus().name() : null
            );
        }
        realtimeEventPublisher.publishEncounterChannel(encounter.getId(), encounterEvent);
    }

    private void publishAppointmentRealtime(Appointment appointment, AppointmentStatus previousStatus) {
        if (appointment == null || appointment.getBranch() == null || appointment.getVisitDate() == null) {
            return;
        }

        realtimeEventPublisher.publishAppointmentSummaryChanged(
                appointment.getBranch().getId(),
                appointment.getVisitDate()
        );
        realtimeEventPublisher.publishAppointmentUpdated(
                appointment.getId(),
                appointment.getBranch().getId(),
                appointment.getDoctor() != null ? appointment.getDoctor().getId() : null,
                appointment.getVisitDate(),
                appointment.getSession() != null ? appointment.getSession().name() : null,
                previousStatus != null ? previousStatus.name() : null,
                appointment.getStatus() != null ? appointment.getStatus().name() : null,
                appointment.getArrivalStatus() != null ? appointment.getArrivalStatus().name() : null,
                appointment.getQueueNo(),
                appointment.getReceptionQueueNo(),
                appointment.getArrivedAt() != null ? appointment.getArrivedAt().toString() : null,
                appointment.getArrivedBy() != null ? resolveUserDisplayName(appointment.getArrivedBy()) : null,
                appointment.getCheckedInAt() != null ? appointment.getCheckedInAt().toString() : null,
                appointment.getCheckedInBy() != null ? resolveUserDisplayName(appointment.getCheckedInBy()) : null,
                appointment.getConfirmedAt() != null ? appointment.getConfirmedAt().toString() : null,
                appointment.getConfirmedBy() != null ? resolveUserDisplayName(appointment.getConfirmedBy()) : null
        );
    }

    private void applyClinicalData(Encounter e, UpdateEncounterRequest req) {
        if (req.getIntakeReasonForVisit() != null) {
            e.setIntakeReasonForVisit(normalize(req.getIntakeReasonForVisit()));
        }
        if (req.getVisitType() != null) {
            e.setVisitType(normalizeCode(req.getVisitType()));
        }
        if (req.getTriagePriority() != null) {
            e.setTriagePriority(normalizeCode(req.getTriagePriority()));
        }
        if (req.getTriageNote() != null) {
            e.setTriageNote(normalize(req.getTriageNote()));
        }
        if (req.getInsuranceNote() != null) {
            e.setInsuranceNote(normalize(req.getInsuranceNote()));
        }
        if (req.getEmergencyContactName() != null) {
            e.setEmergencyContactName(normalize(req.getEmergencyContactName()));
        }
        if (req.getEmergencyContactPhone() != null) {
            e.setEmergencyContactPhone(normalize(req.getEmergencyContactPhone()));
        }
        if (req.getChiefComplaint() != null) {
            e.setChiefComplaint(normalize(req.getChiefComplaint()));
        }
        if (req.getClinicalNote() != null) {
            e.setClinicalNote(normalize(req.getClinicalNote()));
        }
        if (req.getPreliminaryDiagnosis() != null) {
            e.setPreliminaryDiagnosis(normalize(req.getPreliminaryDiagnosis()));
        }
        if (req.getFinalDiagnosis() != null) {
            e.setFinalDiagnosis(normalize(req.getFinalDiagnosis()));
        }
        if (req.getConclusion() != null) {
            e.setConclusion(normalize(req.getConclusion()));
        }

        if (req.getHeightCm() != null) {
            e.setHeightCm(req.getHeightCm());
        }
        if (req.getWeightKg() != null) {
            e.setWeightKg(req.getWeightKg());
        }
        if (req.getTemperatureC() != null) {
            e.setTemperatureC(req.getTemperatureC());
        }
        if (req.getPulse() != null) {
            e.setPulse(req.getPulse());
        }
        if (req.getSystolicBp() != null) {
            e.setSystolicBp(req.getSystolicBp());
        }
        if (req.getDiastolicBp() != null) {
            e.setDiastolicBp(req.getDiastolicBp());
        }
        if (req.getRespiratoryRate() != null) {
            e.setRespiratoryRate(req.getRespiratoryRate());
        }
        if (req.getSpo2() != null) {
            e.setSpo2(req.getSpo2());
        }

        if (req.getAllergySnapshot() != null) {
            e.setAllergySnapshot(normalize(req.getAllergySnapshot()));
        }
        if (req.getChronicDiseaseSnapshot() != null) {
            e.setChronicDiseaseSnapshot(normalize(req.getChronicDiseaseSnapshot()));
        }
        if (req.getPastMedicalHistory() != null) {
            e.setPastMedicalHistory(normalize(req.getPastMedicalHistory()));
        }
        if (req.getPhysicalExamination() != null) {
            e.setPhysicalExamination(normalize(req.getPhysicalExamination()));
        }
        if (req.getTreatmentPlan() != null) {
            e.setTreatmentPlan(normalize(req.getTreatmentPlan()));
        }
        if (req.getFollowUpDate() != null) {
            e.setFollowUpDate(req.getFollowUpDate());
        }
        if (req.getFollowUpNote() != null) {
            e.setFollowUpNote(normalize(req.getFollowUpNote()));
        }
    }


    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        return StringUtil.trimToNull(value);
    }

    private String normalizeCode(String value) {
        String normalized = StringUtil.trimToNull(value);
        return normalized != null ? normalized.toUpperCase().replace(' ', '_') : null;
    }

    private String generateCode(Appointment appointment) {
        return "ENC"
                + appointment.getVisitDate().format(DateTimeFormatter.ofPattern("yyMMdd"))
                + String.format("%06d", appointment.getId());
    }

    private String resolveUserDisplayName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getStaffProfile() != null && user.getStaffProfile().getFullName() != null) {
            return user.getStaffProfile().getFullName();
        }
        if (user.getDoctorProfile() != null && user.getDoctorProfile().getFullName() != null) {
            return user.getDoctorProfile().getFullName();
        }
        return user.getEmail();
    }

    private EncounterResponse toResponse(Encounter e) {
        return toResponse(e, encounterWorkflowService.getWorkflowState(e));
    }

    private EncounterResponse toResponse(Encounter e, EncounterWorkflowState workflowState) {
        return EncounterResponse.builder()
                                .id(e.getId())
                                .code(e.getCode())
                                .appointmentId(e.getAppointment().getId())
                                .patientId(e.getPatient() != null ? e.getPatient().getId() : null)
                                .doctorId(e.getDoctor().getId())
                                .doctorName(e.getDoctor().getFullName())
                                .branchId(e.getBranch().getId())
                                .branchNameVn(e.getBranch().getNameVn())
                                .branchNameEn(e.getBranch().getNameEn())
                                .specialtyId(e.getSpecialty().getId())
                                .specialtyNameVn(e.getSpecialty().getNameVn())
                                .specialtyNameEn(e.getSpecialty().getNameEn())
                                .patientFullName(e.getPatientFullNameSnapshot())
                                .patientPhone(e.getPatientPhoneSnapshot())
                                .patientEmail(e.getPatientEmailSnapshot())
                                .patientDob(e.getPatientDobSnapshot())
                                .patientGender(e.getPatientGenderSnapshot() != null ? e.getPatientGenderSnapshot().name() : null)
                                .intakeReasonForVisit(e.getIntakeReasonForVisit())
                                .visitType(e.getVisitType())
                                .triagePriority(e.getTriagePriority())
                                .triageNote(e.getTriageNote())
                                .receptionPriority(e.getTriagePriority())
                                .receptionNote(e.getTriageNote())
                                .insuranceNote(e.getInsuranceNote())
                                .emergencyContactName(e.getEmergencyContactName())
                                .emergencyContactPhone(e.getEmergencyContactPhone())
                                .intakeCompletedAt(e.getIntakeCompletedAt())
                                .intakeCompletedByName(e.getIntakeCompletedByName())
                                .chiefComplaint(e.getChiefComplaint())
                                .clinicalNote(e.getClinicalNote())
                                .preliminaryDiagnosis(e.getPreliminaryDiagnosis())
                                .finalDiagnosis(e.getFinalDiagnosis())
                                .conclusion(e.getConclusion())
                                .heightCm(e.getHeightCm())
                                .weightKg(e.getWeightKg())
                                .temperatureC(e.getTemperatureC())
                                .pulse(e.getPulse())
                                .systolicBp(e.getSystolicBp())
                                .diastolicBp(e.getDiastolicBp())
                                .respiratoryRate(e.getRespiratoryRate())
                                .spo2(e.getSpo2())
                                .allergySnapshot(e.getAllergySnapshot())
                                .chronicDiseaseSnapshot(e.getChronicDiseaseSnapshot())
                                .pastMedicalHistory(e.getPastMedicalHistory())
                                .physicalExamination(e.getPhysicalExamination())
                                .treatmentPlan(e.getTreatmentPlan())
                                .followUpDate(e.getFollowUpDate())
                                .followUpNote(e.getFollowUpNote())
                                .status(e.getStatus())
                                .startedAt(e.getStartedAt())
                                .completedAt(e.getCompletedAt())
                                .createdAt(e.getCreatedAt())
                                .updatedAt(e.getUpdatedAt())
                                .diagnoses(loadDiagnoses(e.getId()))
                                .serviceOrderCount(workflowState.serviceOrderCount())
                                .pendingPaymentOrderCount(workflowState.pendingPaymentOrderCount())
                                .activeServiceOrderCount(workflowState.activeServiceOrderCount())
                                .waitingResultItemCount(workflowState.waitingResultItemCount())
                                .completedResultItemCount(workflowState.completedResultItemCount())
                                .issuedPrescriptionCount(workflowState.issuedPrescriptionCount())
                                .hasPendingPayment(workflowState.hasPendingPayment())
                                .hasWaitingResults(workflowState.hasWaitingResults())
                                .readyForConclusion(workflowState.readyForConclusion())
                                .canCreatePrescription(workflowState.canCreatePrescription())
                                .canComplete(workflowState.canComplete())
                                .build();
    }

    private java.util.List<EncounterDiagnosisResponse> loadDiagnoses(Long encounterId) {
        return encounterDiagnosisRepository.findWithIcd10ByEncounterId(encounterId)
                                           .stream()
                                           .map(d -> EncounterDiagnosisResponse.builder()
                                                   .id(d.getId())
                                                   .icd10CodeId(d.getIcd10Code().getId())
                                                   .icd10Code(d.getIcd10Code().getCode())
                                                   .icd10NameVn(d.getIcd10Code().getNameVn())
                                                   .icd10NameEn(d.getIcd10Code().getNameEn())
                                                   .diagnosisType(d.getDiagnosisType())
                                                   .note(d.getNote())
                                                   .displayOrder(d.getDisplayOrder())
                                                   .build())
                                           .toList();
    }

    @Transactional
    public EncounterResponse reopenEncounter(Long encounterId, Long userId, String reason) {
        String normalizedReason = normalize(reason);
        if (normalizedReason == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Vui lòng nhập lý do mở lại hồ sơ khám");
        }

        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ApiException(ErrorCode.ENCOUNTER_NOT_FOUND));

        if (encounter.getStatus() != EncounterStatus.COMPLETED) {
            throw new ApiException(ErrorCode.ENCOUNTER_REOPEN_NOT_ALLOWED, "Chỉ có thể mở lại lần khám đã hoàn tất");
        }

        int currentReopenCount = encounter.getReopenedCount() == null ? 0 : encounter.getReopenedCount();
        if (currentReopenCount >= maxReopenCount) {
            throw new ApiException(ErrorCode.ENCOUNTER_REOPEN_NOT_ALLOWED, "Lần khám đã vượt quá số lần mở lại cho phép");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        if (user.getRole() == UserRole.DOCTOR) {
            Long requesterDoctorId = user.getDoctorProfile() != null ? user.getDoctorProfile().getId() : null;
            Long encounterDoctorId = encounter.getDoctor() != null ? encounter.getDoctor().getId() : null;
            if (requesterDoctorId == null || encounterDoctorId == null || !requesterDoctorId.equals(encounterDoctorId)) {
                throw new ApiException(ErrorCode.ACCESS_DENIED, "Bác sĩ chỉ được mở lại hồ sơ khám do mình phụ trách");
            }
        } else if (user.getRole() != UserRole.OPERATIONS_ADMIN && user.getRole() != UserRole.SYSTEM_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền mở lại hồ sơ khám này");
        }

        Map<String, Object> before = snapshotEncounter(encounter);

        // Log the reopen action
        EncounterReopenLog log = EncounterReopenLog.builder()
                .encounter(encounter)
                .reopenedByUser(user)
                .reason(normalizedReason)
                .previousStatus(encounter.getStatus().name())
                .reopenedAt(LocalDateTime.now())
                .build();
        encounterReopenLogRepository.save(log);

        // Reopen clinical documentation only. The completed appointment remains completed for KPI,
        // patient portal, billing and queue semantics.
        encounter.setStatus(EncounterStatus.REOPENED);
        encounter.setReopenedCount(currentReopenCount + 1);
        Encounter saved = encounterRepository.save(encounter);
        auditLogService.log(user, "REOPEN", "ENCOUNTER", saved.getId(), before, snapshotEncounter(saved));

        afterCommitExecutor.execute(() -> publishEncounterRealtime(saved, "ENCOUNTER_REOPENED"));

        return toResponse(saved);
    }

    private Map<String, Object> snapshotEncounter(Encounter encounter) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", encounter.getId());
        data.put("code", encounter.getCode());
        data.put("appointmentId", encounter.getAppointment() != null ? encounter.getAppointment().getId() : null);
        data.put("doctorId", encounter.getDoctor() != null ? encounter.getDoctor().getId() : null);
        data.put("patientId", encounter.getPatient() != null ? encounter.getPatient().getId() : null);
        data.put("status", encounter.getStatus() != null ? encounter.getStatus().name() : null);
        data.put("completedAt", encounter.getCompletedAt());
        data.put("reopenedCount", encounter.getReopenedCount());
        data.put("chiefComplaint", encounter.getChiefComplaint());
        data.put("clinicalNote", encounter.getClinicalNote());
        data.put("preliminaryDiagnosis", encounter.getPreliminaryDiagnosis());
        data.put("finalDiagnosis", encounter.getFinalDiagnosis());
        data.put("conclusion", encounter.getConclusion());
        data.put("treatmentPlan", encounter.getTreatmentPlan());
        data.put("followUpDate", encounter.getFollowUpDate());
        data.put("followUpNote", encounter.getFollowUpNote());
        data.put("updatedAt", encounter.getUpdatedAt());
        return data;
    }
}
