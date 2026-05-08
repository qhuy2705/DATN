package com.PrimeCare.PrimeCare.modules.staff.dto.query;

import com.PrimeCare.PrimeCare.shared.enums.StaffStatus;

public interface StaffStatusCountRow {
    StaffStatus getStatus();

    long getCount();
}
