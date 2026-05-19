package com.PrimeCare.PrimeCare.modules.appointment.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class FollowUpQueueQueryTest {

    @Test
    void searchFollowUpQueueFiltersPendingWorkAndUsesCategoryTypeSet() throws Exception {
        String query = searchFollowUpQueueQuery();

        assertThat(query).contains("a.followUpPending = true");
        assertThat(query).contains("a.followUpType in :followUpTypes");
        assertThat(query).contains(":includeLegacyNoShow = true");
    }

    @Test
    void searchFollowUpQueueSupportsBackendKeywordSearch() throws Exception {
        String query = searchFollowUpQueueQuery();

        assertThat(query).contains("lower(a.code)");
        assertThat(query).contains("lower(coalesce(a.patientFullName, ''))");
        assertThat(query).contains("lower(coalesce(a.patientPhone, ''))");
        assertThat(query).contains("lower(coalesce(a.patientEmail, ''))");
        assertThat(query).contains("lower(coalesce(a.doctor.fullName, ''))");
        assertThat(query).contains("lower(coalesce(a.specialty.nameVn, ''))");
        assertThat(query).contains("lower(coalesce(a.specialty.nameEn, ''))");
    }

    private String searchFollowUpQueueQuery() throws Exception {
        Method method = AppointmentRepository.class.getMethod(
                "searchFollowUpQueue",
                Collection.class,
                boolean.class,
                boolean.class,
                String.class,
                String.class,
                Pageable.class
        );
        assertThat(method.getReturnType()).isEqualTo(Page.class);
        assertThat(method.getParameterTypes()[0]).isEqualTo(Collection.class);
        return method.getAnnotation(Query.class).value();
    }
}
