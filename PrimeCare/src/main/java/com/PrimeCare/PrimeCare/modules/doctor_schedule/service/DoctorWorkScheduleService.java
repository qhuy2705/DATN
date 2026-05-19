package com.PrimeCare.PrimeCare.modules.doctor_schedule.service;

import com.PrimeCare.PrimeCare.config.PaginationConfig;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.doctor_leave.service.DoctorLeaveRequestService;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.request.UpsertDoctorWorkScheduleRequest;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.response.DoctorScheduleDoctorOptionResponse;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.response.DoctorScheduleImportResponse;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.response.DoctorWorkScheduleResponse;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.entity.DoctorWorkSchedule;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.repository.DoctorWorkScheduleRepository;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.util.DoctorScheduleExcelParser;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchSessionRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.entity.DoctorProfile;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.service.DoctorOperationalGuardService;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import com.PrimeCare.PrimeCare.shared.exception.ApiException;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DoctorWorkScheduleService {

    private final DoctorWorkScheduleRepository doctorWorkScheduleRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final UserRepository userRepository;
    private final DoctorLeaveRequestService doctorLeaveRequestService;
    private final BranchSessionRepository branchSessionRepository;
    private final AuditLogService auditLogService;
    private final DoctorOperationalGuardService doctorOperationalGuardService;

    @Transactional
    public DoctorWorkScheduleResponse upsert(UpsertDoctorWorkScheduleRequest request) {
        DoctorProfile doctor = doctorProfileRepository.findById(request.getDoctorId())
                                                      .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));
        doctorOperationalGuardService.assertDoctorBookable(doctor);
        Map<String, Object> before = doctorWorkScheduleRepository
                .findByDoctor_IdAndWorkDateAndSession(doctor.getId(), request.getWorkDate(), request.getSession())
                .map(this::snapshotSchedule)
                .orElse(null);

        DoctorWorkSchedule saved = upsertEntity(doctor, request);
        auditLogService.log(null, "UPSERT_DOCTOR_SCHEDULE", "DOCTOR_SCHEDULE", saved.getId(), before, snapshotSchedule(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long doctorId, LocalDate workDate, BranchSessionType session) {
        var existing = doctorWorkScheduleRepository.findByDoctor_IdAndWorkDateAndSession(doctorId, workDate, session);
        if (existing.isEmpty()) {
            return;
        }

        Long scheduleId = existing.get().getId();
        Map<String, Object> before = snapshotSchedule(existing.get());
        doctorWorkScheduleRepository.deleteByDoctor_IdAndWorkDateAndSession(doctorId, workDate, session);
        auditLogService.log(null, "DELETE_DOCTOR_SCHEDULE", "DOCTOR_SCHEDULE", scheduleId, before, null);
    }

    @Transactional(readOnly = true)
    public java.util.List<DoctorScheduleDoctorOptionResponse> getDoctorOptions() {
        return doctorProfileRepository.search(
                                              null,
                                              null,
                                              null,
                                              DoctorStatus.ACTIVE,
                                              Pageable.unpaged()
                                      )
                                      .getContent()
                                      .stream()
                                      .map(doctor -> DoctorScheduleDoctorOptionResponse.builder()
                                                                                       .id(doctor.getId())
                                                                                       .fullName(doctor.getFullName())
                                                                                       .displayTitleVn(doctor.getDisplayTitleVn())
                                                                                       .branchId(doctor.getBranch() != null ? doctor.getBranch().getId() : null)
                                                                                       .branchNameVn(doctor.getBranch() != null ? doctor.getBranch().getNameVn() : null)
                                                                                       .status((doctor.getStatus() != null ? doctor.getStatus() : DoctorStatus.ACTIVE).name())
                                                                                       .build())
                                      .sorted(java.util.Comparator.comparing(DoctorScheduleDoctorOptionResponse::getFullName, java.lang.String.CASE_INSENSITIVE_ORDER))
                                      .toList();
    }

    @Transactional(readOnly = true)
    public Page<DoctorWorkScheduleResponse> getSchedules(Long doctorId, LocalDate from, LocalDate to, int page, int size) {
        return doctorWorkScheduleRepository.findByDoctor_IdAndWorkDateBetween(
                                                   doctorId,
                                                   from,
                                                   to,
                                                   PaginationConfig.pageRequest(page, size, Sort.by("workDate").ascending().and(Sort.by("session").ascending()))
                                           )
                                           .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<DoctorWorkScheduleResponse> getMySchedules(Long currentUserId, LocalDate from, LocalDate to, int page, int size) {
        User user = userRepository.findById(currentUserId).orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));
        if (user.getDoctorProfile() == null) {
            throw new ApiException(ErrorCode.DOCTOR_NOT_FOUND);
        }
        return getSchedules(user.getDoctorProfile().getId(), from, to, page, size);
    }

    @Transactional
    public DoctorScheduleImportResponse importMonthSchedules(
            Long doctorId,
            LocalDate month,
            boolean clearMonthFirst,
            MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Vui lòng chọn file Excel để nhập lịch làm việc");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!fileName.endsWith(".xlsx")) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chỉ hỗ trợ file Excel định dạng .xlsx");
        }

        DoctorProfile doctor = doctorProfileRepository.findById(doctorId)
                                                      .orElseThrow(() -> new ApiException(ErrorCode.DOCTOR_NOT_FOUND));
        doctorOperationalGuardService.assertDoctorBookable(doctor);

        LocalDate monthStart = month.withDayOfMonth(1);
        LocalDate monthEnd = month.withDayOfMonth(month.lengthOfMonth());

        List<DoctorScheduleExcelParser.ParsedScheduleRow> rows;
        try {
            rows = DoctorScheduleExcelParser.parse(file.getInputStream());
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Không thể đọc file Excel lịch làm việc");
        }

        int clearedRows = 0;
        if (clearMonthFirst) {
            clearedRows = doctorWorkScheduleRepository
                    .findByDoctor_IdAndWorkDateBetweenOrderByWorkDateAscSessionAsc(doctorId, monthStart, monthEnd)
                    .size();
            doctorWorkScheduleRepository.deleteByDoctor_IdAndWorkDateBetween(doctorId, monthStart, monthEnd);
        }

        int importedRows = 0;
        int createdRows = 0;
        int updatedRows = 0;
        int skippedRows = 0;
        List<String> warnings = new ArrayList<>();
        Set<String> duplicateGuard = new HashSet<>();

        for (DoctorScheduleExcelParser.ParsedScheduleRow row : rows) {
            if (row.workDate() == null) {
                skippedRows += 1;
                warnings.add("Dòng " + row.rowNumber() + ": ngày làm việc không hợp lệ. Hãy dùng định dạng yyyy-MM-dd hoặc dd/MM/yyyy.");
                continue;
            }
            if (row.session() == null) {
                skippedRows += 1;
                warnings.add("Dòng " + row.rowNumber() + ": ca làm việc không hợp lệ. Dùng AM/PM, MORNING/AFTERNOON hoặc SANG/CHIEU.");
                continue;
            }
            if (row.workDate().isBefore(monthStart) || row.workDate().isAfter(monthEnd)) {
                skippedRows += 1;
                warnings.add("Dòng " + row.rowNumber() + ": ngày làm việc nằm ngoài tháng đang nhập.");
                continue;
            }

            String duplicateKey = row.workDate() + "|" + row.session().name();
            if (!duplicateGuard.add(duplicateKey)) {
                skippedRows += 1;
                warnings.add("Dòng " + row.rowNumber() + ": trùng ngày và ca với một dòng trước đó trong file.");
                continue;
            }

            boolean blockedByApprovedLeave = doctorLeaveRequestService.hasApprovedLeaveForSession(
                    doctorId,
                    row.workDate(),
                    row.session()
            );
            if (blockedByApprovedLeave) {
                skippedRows += 1;
                warnings.add("Dòng " + row.rowNumber() + ": buổi này đang bị chặn do đơn nghỉ đã duyệt.");
                continue;
            }

            UpsertDoctorWorkScheduleRequest request = new UpsertDoctorWorkScheduleRequest();
            request.setDoctorId(doctorId);
            request.setWorkDate(row.workDate());
            request.setSession(row.session());
            request.setNote(row.note());

            boolean existed = doctorWorkScheduleRepository
                    .findByDoctor_IdAndWorkDateAndSession(doctorId, row.workDate(), row.session())
                    .isPresent();
            upsertEntity(doctor, request);
            importedRows += 1;
            if (existed) {
                updatedRows += 1;
            } else {
                createdRows += 1;
            }
        }

        DoctorScheduleImportResponse response = DoctorScheduleImportResponse.builder()
                                                                            .doctorId(doctor.getId())
                                                                            .doctorName(doctor.getFullName())
                                                                            .month(monthStart.toString())
                                                                            .totalRows(rows.size())
                                                                            .importedRows(importedRows)
                                                                            .skippedRows(skippedRows)
                                                                            .warnings(warnings)
                                                                            .build();

        if (importedRows > 0 || clearedRows > 0) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("doctorId", doctor.getId());
            summary.put("branchId", doctor.getBranch() != null ? doctor.getBranch().getId() : null);
            summary.put("month", monthStart.toString());
            summary.put("clearMonthFirst", clearMonthFirst);
            summary.put("totalRows", rows.size());
            summary.put("totalCreated", createdRows);
            summary.put("totalUpdated", updatedRows);
            summary.put("totalSkipped", skippedRows);
            summary.put("clearedRows", clearedRows);
            auditLogService.log(null, "IMPORT_DOCTOR_SCHEDULE", "DOCTOR_SCHEDULE", doctor.getId(), null, summary);
        }

        return response;
    }

    private DoctorWorkSchedule upsertEntity(DoctorProfile doctor, UpsertDoctorWorkScheduleRequest request) {
        boolean blockedByApprovedLeave = doctorLeaveRequestService.hasApprovedLeaveForSession(
                doctor.getId(),
                request.getWorkDate(),
                request.getSession()
        );
        if (blockedByApprovedLeave) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Không thể tạo lịch làm việc trong buổi đã được duyệt nghỉ phép");
        }

        DoctorWorkSchedule schedule = doctorWorkScheduleRepository
                .findByDoctor_IdAndWorkDateAndSession(
                        doctor.getId(),
                        request.getWorkDate(),
                        request.getSession()
                )
                .orElse(
                        DoctorWorkSchedule.builder()
                                          .doctor(doctor)
                                          .workDate(request.getWorkDate())
                                          .session(request.getSession())
                                          .build()
                );

        schedule.setNote(request.getNote());

        return doctorWorkScheduleRepository.save(schedule);
    }

    private DoctorWorkScheduleResponse toResponse(DoctorWorkSchedule entity) {
        Long branchId = entity.getDoctor() != null && entity.getDoctor().getBranch() != null
                ? entity.getDoctor().getBranch().getId()
                : null;
        var sessionWindow = branchId != null && entity.getSession() != null
                ? branchSessionRepository.findByBranch_IdAndSessionAndStatus(branchId, entity.getSession(), "ACTIVE").orElse(null)
                : null;

        return DoctorWorkScheduleResponse.builder()
                                         .id(entity.getId())
                                         .doctorId(entity.getDoctor().getId())
                                         .doctorName(entity.getDoctor() != null ? entity.getDoctor().getFullName() : null)
                                         .branchId(branchId)
                                         .branchName(entity.getDoctor() != null && entity.getDoctor().getBranch() != null ? entity.getDoctor().getBranch().getNameVn() : null)
                                         .workDate(entity.getWorkDate())
                                         .session(entity.getSession())
                                         .startTime(sessionWindow != null ? sessionWindow.getStartTime() : null)
                                         .endTime(sessionWindow != null ? sessionWindow.getEndTime() : null)
                                         .note(entity.getNote())
                                         .build();
    }

    private Map<String, Object> snapshotSchedule(DoctorWorkSchedule entity) {
        Long branchId = entity.getDoctor() != null && entity.getDoctor().getBranch() != null
                ? entity.getDoctor().getBranch().getId()
                : null;
        var sessionWindow = branchId != null && entity.getSession() != null
                ? branchSessionRepository.findByBranch_IdAndSessionAndStatus(branchId, entity.getSession(), "ACTIVE").orElse(null)
                : null;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", entity.getId());
        data.put("doctorId", entity.getDoctor() != null ? entity.getDoctor().getId() : null);
        data.put("branchId", branchId);
        data.put("workDate", entity.getWorkDate());
        data.put("dayOfWeek", entity.getWorkDate() != null ? entity.getWorkDate().getDayOfWeek().name() : null);
        data.put("session", entity.getSession() != null ? entity.getSession().name() : null);
        data.put("startTime", sessionWindow != null ? sessionWindow.getStartTime() : null);
        data.put("endTime", sessionWindow != null ? sessionWindow.getEndTime() : null);
        data.put("note", entity.getNote());
        return data;
    }
}
