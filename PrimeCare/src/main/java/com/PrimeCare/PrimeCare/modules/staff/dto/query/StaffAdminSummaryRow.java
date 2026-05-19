package com.PrimeCare.PrimeCare.modules.staff.dto.query;

public interface StaffAdminSummaryRow {
    long getTotal();

    long getActive();

    long getInactive();

    long getNoAccountStaffs();

    long getInactiveAccountStaffs();
}
