package com.PrimeCare.PrimeCare.modules.masterdata.branch.dto.query;

import com.PrimeCare.PrimeCare.shared.enums.BranchStatus;

public interface BranchStatusCountRow {
    BranchStatus getStatus();

    long getCount();
}
