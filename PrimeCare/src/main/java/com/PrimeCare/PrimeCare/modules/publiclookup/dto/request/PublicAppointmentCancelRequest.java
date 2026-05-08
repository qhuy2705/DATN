package com.PrimeCare.PrimeCare.modules.publiclookup.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicAppointmentCancelRequest {
    @Size(max = 500, message = "Lý do hủy tối đa 500 ký tự")
    private String reason;
}
