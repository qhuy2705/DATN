package com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.query;

public interface DoctorAdminSummaryRow {
    long getTotal();

    long getActive();

    long getInactive();

    long getNoAccountDoctors();

    long getInactiveAccountDoctors();

    long getOperationalReadyDoctors();
}
