package com.PrimeCare.PrimeCare.modules.appointment.service;

import com.PrimeCare.PrimeCare.modules.appointment.dto.query.AppointmentStatusCountRow;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.AppointmentAdminResponse;
import com.PrimeCare.PrimeCare.modules.appointment.dto.response.DoctorAppointmentSummaryResponse;
import com.PrimeCare.PrimeCare.modules.appointment.entity.Appointment;
import com.PrimeCare.PrimeCare.modules.appointment.repository.AppointmentRepository;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.encounter.dto.record.EncounterWorkflowState;
import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.encounter.repository.EncounterRepository;
import com.PrimeCare.PrimeCare.modules.encounter.service.EncounterWorkflowService;
import com.PrimeCare.PrimeCare.shared.common.PageResponse;
import com.PrimeCare.PrimeCare.shared.enums.AppointmentStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorAppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final EncounterRepository encounterRepository;
    private final EncounterWorkflowService encounterWorkflowService;

    @Transactional(readOnly = true)
    public PageResponse<AppointmentAdminResponse> myAppointments(Long userId, LocalDate from, LocalDate to, Pageable pageable) {
        long started = System.nanoTime();
        User user = userRepository.findById(userId).orElseThrow(() ->new ApiException(ErrorCode.DOCTOR_NOT_FOUND));
        if (user.getDoctorProfile() == null || user.getDoctorProfile().getId() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        Page<Appointment> page = appointmentRepository.findDoctorCalendar(
                user.getDoctorProfile().getId(),
                from,
                to,
                EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.CHECKED_IN, AppointmentStatus.COMPLETED),
                pageable
        );

        List<Long> appointmentIds = page.getContent().stream().map(Appointment::getId).toList();
        Map<Long, Encounter> encounterByAppointmentId = appointmentIds.isEmpty()
                ? Map.of()
                : encounterRepository.findByAppointment_IdIn(appointmentIds)
                                     .stream()
                                     .collect(Collectors.toMap(
                                             encounter -> encounter.getAppointment().getId(),
                                             Function.identity(),
                                             (left, ignored) -> left
                                     ));
        Map<Long, EncounterWorkflowState> workflowStateByEncounterId = encounterWorkflowService.getWorkflowStates(
                encounterByAppointmentId.values()
        );

        List<AppointmentAdminResponse> items = page.getContent()
                .stream()
                .map(appointment -> toResponse(
                        appointment,
                        encounterByAppointmentId.get(appointment.getId()),
                        workflowStateByEncounterId
                ))
                .toList();

        log.info("doctor appointments durationMs={} doctorId={} from={} to={} size={}",
                (System.nanoTime() - started) / 1_000_000L,
                user.getDoctorProfile().getId(),
                from,
                to,
                page.getNumberOfElements());

        return PageResponse.<AppointmentAdminResponse>builder()
                .items(items)
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
    public DoctorAppointmentSummaryResponse myAppointmentSummary(
            Long userId,
            LocalDate visitDate,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        User user = resolveDoctorUser(userId);
        DateRange range = resolveSummaryRange(visitDate, fromDate, toDate);

        Map<AppointmentStatus, Long> countByStatus = appointmentRepository.countDoctorSummaryByStatus(
                        user.getDoctorProfile().getId(),
                        range.from(),
                        range.to(),
                        EnumSet.allOf(AppointmentStatus.class)
                )
                .stream()
                .collect(Collectors.toMap(AppointmentStatusCountRow::getStatus, AppointmentStatusCountRow::getCount));

        long confirmed = countByStatus.getOrDefault(AppointmentStatus.CONFIRMED, 0L);
        long checkedIn = countByStatus.getOrDefault(AppointmentStatus.CHECKED_IN, 0L);
        long completed = countByStatus.getOrDefault(AppointmentStatus.COMPLETED, 0L);
        long cancelled = countByStatus.getOrDefault(AppointmentStatus.CANCELLED, 0L);
        long noShow = countByStatus.getOrDefault(AppointmentStatus.NO_SHOW, 0L);
        long total = countByStatus.values().stream().mapToLong(Long::longValue).sum();

        return DoctorAppointmentSummaryResponse.builder()
                .fromDate(range.from())
                .toDate(range.to())
                .confirmed(confirmed)
                .checkedIn(checkedIn)
                .waitingExam(checkedIn)
                .completedToday(completed)
                .cancelled(cancelled)
                .noShow(noShow)
                .totalToday(total)
                .build();
    }

    private AppointmentAdminResponse toResponse(
            Appointment entity,
            Encounter activeEncounter,
            Map<Long, EncounterWorkflowState> workflowStateByEncounterId
    ) {
        EncounterWorkflowState workflowState = activeEncounter == null
                ? encounterWorkflowService.getWorkflowState(null)
                : workflowStateByEncounterId.getOrDefault(activeEncounter.getId(), encounterWorkflowService.getWorkflowState(null));

        return AppointmentAdminResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .status(entity.getStatus())
                .branchId(entity.getBranch().getId())
                .branchName(entity.getBranch().getNameVn())
                .specialtyId(entity.getSpecialty().getId())
                .specialtyName(entity.getSpecialty().getNameVn())
                .doctorId(entity.getDoctor().getId())
                .doctorName(entity.getDoctor().getFullName())
                .visitDate(entity.getVisitDate())
                .session(entity.getSession())
                .queueNo(entity.getQueueNo())
                .slotMinutes(entity.getSlotMinutes())
                .etaStart(entity.getEtaStart())
                .etaEnd(entity.getEtaEnd())
                .patientFullName(entity.getPatientFullName())
                .patientPhone(entity.getPatientPhone())
                .patientEmail(entity.getPatientEmail())
                .checkedInAt(entity.getCheckedInAt())
                .followUpPending(Boolean.TRUE.equals(entity.getFollowUpPending()))
                .activeEncounterId(activeEncounter != null ? activeEncounter.getId() : null)
                .activeEncounterCode(activeEncounter != null ? activeEncounter.getCode() : null)
                .activeEncounterStatus(activeEncounter != null ? activeEncounter.getStatus() : null)
                .serviceOrderCount(workflowState.serviceOrderCount())
                .pendingPaymentOrderCount(workflowState.pendingPaymentOrderCount())
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

    private User resolveDoctorUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));
        if (user.getDoctorProfile() == null || user.getDoctorProfile().getId() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private DateRange resolveSummaryRange(LocalDate visitDate, LocalDate fromDate, LocalDate toDate) {
        if (visitDate != null) {
            return new DateRange(visitDate, visitDate);
        }

        LocalDate today = LocalDate.now();
        LocalDate safeFrom = fromDate != null ? fromDate : today;
        LocalDate safeTo = toDate != null ? toDate : safeFrom;
        if (safeTo.isBefore(safeFrom)) {
            safeTo = safeFrom;
        }
        return new DateRange(safeFrom, safeTo);
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
