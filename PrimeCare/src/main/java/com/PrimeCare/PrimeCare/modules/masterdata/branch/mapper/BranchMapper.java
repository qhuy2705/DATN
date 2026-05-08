package com.PrimeCare.PrimeCare.modules.masterdata.branch.mapper;

import com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.response.BranchResponse;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.entity.Branch;

public class BranchMapper {
    public static BranchResponse toResponse(Branch b) {
        BranchResponse r = new BranchResponse();
        r.setId(b.getId());
        r.setCode(b.getCode());
        r.setNameVn(b.getNameVn());
        r.setNameEn(b.getNameEn());
        r.setAddressVn(b.getAddressVn());
        r.setAddressEn(b.getAddressEn());
        r.setPhone(b.getPhone());
        r.setEmail(b.getEmail());

        r.setLatitude(b.getLat() == null ? null : b.getLat().doubleValue());
        r.setLongitude(b.getLng() == null ? null : b.getLng().doubleValue());

        r.setDescriptionVn(b.getDescriptionVn());
        r.setDescriptionEn(b.getDescriptionEn());

        r.setStatus(b.getStatus());
        r.setCreatedAt(b.getCreatedAt());
        r.setUpdatedAt(b.getUpdatedAt());
        return r;
    }
}
