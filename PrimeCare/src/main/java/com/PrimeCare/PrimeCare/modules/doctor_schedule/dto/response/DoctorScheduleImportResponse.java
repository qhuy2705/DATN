package com.PrimeCare.PrimeCare.modules.doctor_schedule.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DoctorScheduleImportResponse {
    private Long doctorId;
    private String doctorName;
    private String month;
    private int totalRows;
    private int importedRows;
    private int skippedRows;
    private List<String> warnings;
}
