package com.PrimeCare.PrimeCare.modules.prescription.dto.response;

import com.PrimeCare.PrimeCare.shared.enums.PrescriptionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class PrescriptionResponse {
    private Long id;
    private String code;
    private Long encounterId;
    private String encounterCode;
    private Long doctorUserId;
    private LocalDate issuedDate;
    private String generalNote;
    private PrescriptionStatus status;
    private List<PrescriptionItemResponse> items;
}
