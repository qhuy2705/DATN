package com.PrimeCare.PrimeCare.modules.publiccontact.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicContactResponse {
    private Long id;
    private String referenceCode;
    private String status;
}
