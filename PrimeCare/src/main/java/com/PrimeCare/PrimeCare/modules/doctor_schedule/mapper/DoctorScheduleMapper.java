package com.PrimeCare.PrimeCare.modules.doctor_schedule.mapper;

import com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.response.DoctorWorkScheduleResponse;
import com.PrimeCare.PrimeCare.modules.doctor_schedule.entity.DoctorWorkSchedule;

public class DoctorScheduleMapper {
    public static DoctorWorkScheduleResponse toResponse(DoctorWorkSchedule entity) {
        return DoctorWorkScheduleResponse.builder()
                .id(entity.getId())
                .doctorId(entity.getDoctor().getId())
                .doctorName(entity.getDoctor().getFullName() != null ? entity.getDoctor().getFullName() : null)
                .branchId(entity.getDoctor().getBranch() != null ? entity.getDoctor().getBranch().getId() : null)
                .branchName(entity.getDoctor().getBranch() != null ? entity.getDoctor().getBranch().getNameVn() : null)
                .workDate(entity.getWorkDate())
                .session(entity.getSession())
                .note(entity.getNote())
                .build();
    }
}
