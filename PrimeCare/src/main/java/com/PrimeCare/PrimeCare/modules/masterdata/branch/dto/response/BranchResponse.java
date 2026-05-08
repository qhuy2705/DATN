package com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.response;


import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BranchResponse {
    private Long id;
    private String code;

    private String nameVn;
    private String nameEn;

    private String addressVn;
    private String addressEn;

    private String phone;
    private String email;

    private Double latitude;
    private Double longitude;

    private String descriptionVn;
    private String descriptionEn;

    private BranchStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
