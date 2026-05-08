package com.PrimeCare.PrimeCare.modules.prescription.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePrescriptionRequest {

    @Size(max = 4000)
    private String generalNote;

    @Valid
    private List<CreatePrescriptionItemRequest> items;
}