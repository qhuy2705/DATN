package com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DoctorScheduleDoctorOptionResponse {
    private Long id;
    private String fullName;
    private String displayTitleVn;
    private Long branchId;
    private String branchNameVn;
    private String status;
}
