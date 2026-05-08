package com.PrimeCare.PrimeCare.modules.doctor_schedule.controller;

import com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.request.UpsertDoctorWorkScheduleRequest;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.response.DoctorScheduleDoctorOptionResponse;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.response.DoctorScheduleImportResponse;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.response.DoctorWorkScheduleResponse;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.service.DoctorWorkScheduleService;
import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/doctor-schedules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATIONS_ADMIN')")
public class AdminDoctorScheduleController {

    private final DoctorWorkScheduleService doctorWorkScheduleService;

    @PostMapping
    public DoctorWorkScheduleResponse upsert(@RequestBody @Valid UpsertDoctorWorkScheduleRequest request) {
        return doctorWorkScheduleService.upsert(request);
    }

    @DeleteMapping
    public void delete(
            @RequestParam Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @RequestParam BranchSessionType session
    ) {
        doctorWorkScheduleService.delete(doctorId, workDate, session);
    }

    @GetMapping("/doctors/options")
    public List<DoctorScheduleDoctorOptionResponse> getDoctorOptions() {
        return doctorWorkScheduleService.getDoctorOptions();
    }

    @GetMapping
    public Page<DoctorWorkScheduleResponse> getSchedules(
            @RequestParam Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return doctorWorkScheduleService.getSchedules(doctorId, from, to, page, size);
    }

    @PostMapping(value = "/import-xlsx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DoctorScheduleImportResponse importSchedules(
            @RequestParam Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            @RequestParam(defaultValue = "false") boolean clearMonthFirst,
            @RequestPart("file") MultipartFile file
    ) {
        return doctorWorkScheduleService.importMonthSchedules(doctorId, month, clearMonthFirst, file);
    }
}
