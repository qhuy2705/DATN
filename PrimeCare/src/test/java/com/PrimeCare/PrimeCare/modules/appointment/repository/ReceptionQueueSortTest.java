package com.PrimeCare.PrimeCare.modules.appointment.repository;

import com.PrimeCare.PrimeCare.shared.enums.AppointmentSourceType;
import com.PrimeCare.PrimeCare.shared.enums.ArrivalStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class ReceptionQueueSortTest {

    @Test
    void arrivedSortsBeforePriority() throws Exception {
        String query = searchReceptionQueueQuery();

        assertThat(query.indexOf("case when a.arrivalStatus"))
                .isLessThan(query.indexOf("when upper(coalesce(a.triagePriority, a.preTriagePriority)) in ('EMERGENCY', 'P1', 'URGENT')"));
    }

    @Test
    void receptionQueueUsesEffectivePriorityFallbackForSuggestedUrgent() throws Exception {
        String query = searchReceptionQueueQuery();

        assertThat(query).contains("when upper(coalesce(a.triagePriority, a.preTriagePriority)) in ('EMERGENCY', 'P1', 'URGENT') then 0");
        assertThat(query).contains("when upper(coalesce(a.triagePriority, a.preTriagePriority)) in ('P2', 'PRIORITY', 'HIGH') then 1");
        assertThat(query).contains("when upper(coalesce(a.triagePriority, a.preTriagePriority)) in ('P3', 'ROUTINE', 'NORMAL') then 2");
    }

    @Test
    void staffConfirmedPriorityWinsOverPreTriagePriority() throws Exception {
        String query = searchReceptionQueueQuery();

        assertThat(query).contains("coalesce(a.triagePriority, a.preTriagePriority)");
        assertThat(query.indexOf("a.triagePriority"))
                .isLessThan(query.indexOf("a.preTriagePriority"));
    }

    @Test
    void receptionQueuePriorityFilterUsesEffectivePriorityGroups() throws Exception {
        String query = searchReceptionQueueQuery();

        assertThat(query).contains(":triagePriorityFilter is null");
        assertThat(query).contains(":triagePriorityFilter = 'URGENT'");
        assertThat(query).contains("upper(coalesce(a.triagePriority, a.preTriagePriority)) in ('EMERGENCY', 'P1', 'URGENT')");
        assertThat(query).contains(":triagePriorityFilter = 'PRIORITY'");
        assertThat(query).contains("upper(coalesce(a.triagePriority, a.preTriagePriority)) in ('P2', 'PRIORITY', 'HIGH')");
        assertThat(query).contains(":triagePriorityFilter = 'ROUTINE'");
        assertThat(query).contains("upper(coalesce(a.triagePriority, a.preTriagePriority)) in ('P3', 'ROUTINE', 'NORMAL')");
    }

    @Test
    void receptionQueueUnclassifiedPriorityFilterRequiresNoPriority() throws Exception {
        String query = searchReceptionQueueQuery();

        assertThat(query).contains(":triagePriorityFilter = 'NONE'");
        assertThat(query).contains("and (a.triagePriority is null or trim(a.triagePriority) = '')");
        assertThat(query).contains("and (a.preTriagePriority is null or trim(a.preTriagePriority) = '')");
    }

    @Test
    void doctorWaitingQueueStillUsesStaffConfirmedTriagePriorityOnly() throws Exception {
        String query = doctorWaitingQueueQuery();

        assertThat(query).contains("when upper(a.triagePriority) in ('EMERGENCY', 'P1', 'URGENT') then 0");
        assertThat(query).doesNotContain("preTriagePriority");
        assertThat(query).doesNotContain("pre_triage_priority");
    }

    @Test
    void samePrioritySortsByEtaStart() throws Exception {
        String query = searchReceptionQueueQuery();

        assertThat(query.indexOf("a.visitDate asc"))
                .isLessThan(query.indexOf("a.etaStart asc"));
    }

    @Test
    void samePriorityAndEtaSortsByArrivedAt() throws Exception {
        String query = searchReceptionQueueQuery();

        assertThat(query.indexOf("a.etaStart asc"))
                .isLessThan(query.indexOf("a.arrivedAt asc"));
        assertThat(query.indexOf("a.arrivedAt asc"))
                .isLessThan(query.indexOf("a.receptionQueueNo asc"));
        assertThat(query.indexOf("a.receptionQueueNo asc"))
                .isLessThan(query.indexOf("a.queueNo asc"));
        assertThat(query.indexOf("a.queueNo asc"))
                .isLessThan(query.indexOf("a.createdAt asc"));
        assertThat(query.indexOf("a.createdAt asc"))
                .isLessThan(query.indexOf("a.id asc"));
    }

    @Test
    void overdueFilterUsesEtaEndNotEtaStart() throws Exception {
        String query = searchReceptionQueueQuery();

        assertThat(query).contains("a.etaEnd < :overdueCutoffTime");
        assertThat(query).doesNotContain("a.etaStart < :overdueCutoffTime");
    }

    private String searchReceptionQueueQuery() throws Exception {
        Method method = AppointmentRepository.class.getMethod(
                "searchReceptionQueue",
                LocalDate.class,
                Long.class,
                Long.class,
                Long.class,
                ArrivalStatus.class,
                AppointmentSourceType.class,
                String.class,
                Collection.class,
                boolean.class,
                LocalDate.class,
                LocalTime.class,
                String.class,
                String.class,
                Pageable.class
        );
        assertThat(method.getReturnType()).isEqualTo(Page.class);
        return method.getAnnotation(Query.class).value();
    }

    private String doctorWaitingQueueQuery() throws Exception {
        Method method = AppointmentRepository.class.getMethod(
                "findDoctorWaitingQueue",
                Long.class,
                LocalDate.class,
                Pageable.class
        );
        assertThat(method.getReturnType()).isEqualTo(Page.class);
        return method.getAnnotation(Query.class).value();
    }
}
