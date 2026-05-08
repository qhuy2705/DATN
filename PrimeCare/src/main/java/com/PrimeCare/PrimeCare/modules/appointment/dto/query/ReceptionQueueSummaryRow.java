package com.PrimeCare.PrimeCare.modules.appointment.dto.query;

public interface ReceptionQueueSummaryRow {
    long getTotal();

    long getRequested();

    long getConfirmed();

    long getCheckedIn();

    long getArrived();

    long getNotArrived();

    long getWalkIn();

    long getPriority();

    long getUrgent();
}
