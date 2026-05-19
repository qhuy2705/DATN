package com.PrimeCare.PrimeCare.modules.masterdata.doctor.service;

import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.auth.service.AccountProvisionService;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.dto.query.DoctorAdminSummaryRow;
import com.PrimeCare.PrimeCare.modules.masterdata.doctor.repository.DoctorProfileRepository;
import com.PrimeCare.PrimeCare.modules.masterdata.specialty.repository.SpecialtyRepository;
import com.PrimeCare.PrimeCare.shared.enums.DoctorStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorAdminServiceSummaryTest {

    @Mock
    private DoctorProfileRepository doctorProfileRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountProvisionService accountProvisionService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DoctorAdminService service;

    @Test
    void summaryReturnsAccountCountsFromFullFilteredAggregate() {
        DoctorAdminSummaryRow row = mock(DoctorAdminSummaryRow.class);
        when(row.getTotal()).thenReturn(5L);
        when(row.getActive()).thenReturn(4L);
        when(row.getInactive()).thenReturn(1L);
        when(row.getNoAccountDoctors()).thenReturn(2L);
        when(row.getInactiveAccountDoctors()).thenReturn(1L);
        when(row.getOperationalReadyDoctors()).thenReturn(2L);
        when(doctorProfileRepository.summarizeAdmin(10L, 20L, "cardio", DoctorStatus.ACTIVE)).thenReturn(row);

        var response = service.summary(10L, 20L, " cardio ", DoctorStatus.ACTIVE);

        assertThat(response.getTotal()).isEqualTo(5L);
        assertThat(response.getActive()).isEqualTo(4L);
        assertThat(response.getInactive()).isEqualTo(1L);
        assertThat(response.getNoAccountDoctors()).isEqualTo(2L);
        assertThat(response.getInactiveAccountDoctors()).isEqualTo(1L);
        assertThat(response.getOperationalReadyDoctors()).isEqualTo(2L);
        assertThat(response.getNotOperationalReadyDoctors()).isEqualTo(3L);
        verify(doctorProfileRepository).summarizeAdmin(10L, 20L, "cardio", DoctorStatus.ACTIVE);
        verify(doctorProfileRepository, never()).searchAdmin(any(), any(), any(), any(), any());
    }
}
