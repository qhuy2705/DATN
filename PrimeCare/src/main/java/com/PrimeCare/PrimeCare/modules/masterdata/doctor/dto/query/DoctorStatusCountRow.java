package com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.query;

import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;

public interface DoctorStatusCountRow {
    DoctorStatus getStatus();

    long getCount();
}
