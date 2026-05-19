package com.PrimeCare.PrimeCare.modules.staff.service;

import com.PrimeCare.PrimeCare.modules.auth.repository.UserRepository;
import com.PrimeCare.PrimeCare.modules.auth.service.AccountProvisionService;
import com.PrimeCare.PrimeCare.modules.audit.service.AuditLogService;
import com.PrimeCare.PrimeCare.modules.masterdata.branch.repository.BranchRepository;
import com.PrimeCare.PrimeCare.modules.staff.dto.query.StaffAdminSummaryRow;
import com.PrimeCare.PrimeCare.modules.staff.repository.StaffProfileRepository;
import com.PrimeCare.PrimeCare.shared.enums.StaffStatus;
import com.PrimeCare.PrimeCare.shared.enums.UserRole;
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
class StaffAdminServiceSummaryTest {

    @Mock
    private StaffProfileRepository staffProfileRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountProvisionService accountProvisionService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private StaffAdminService service;

    @Test
    void summaryReturnsAccountCountsFromFullFilteredAggregate() {
        StaffAdminSummaryRow row = mock(StaffAdminSummaryRow.class);
        when(row.getTotal()).thenReturn(6L);
        when(row.getActive()).thenReturn(5L);
        when(row.getInactive()).thenReturn(1L);
        when(row.getNoAccountStaffs()).thenReturn(3L);
        when(row.getInactiveAccountStaffs()).thenReturn(2L);
        when(staffProfileRepository.summarizeAdmin(10L, "front desk", StaffStatus.ACTIVE, null)).thenReturn(row);

        var response = service.summary(10L, " front desk ", StaffStatus.ACTIVE);

        assertThat(response.getTotal()).isEqualTo(6L);
        assertThat(response.getActive()).isEqualTo(5L);
        assertThat(response.getInactive()).isEqualTo(1L);
        assertThat(response.getNoAccountStaffs()).isEqualTo(3L);
        assertThat(response.getInactiveAccountStaffs()).isEqualTo(2L);
        verify(staffProfileRepository).summarizeAdmin(10L, "front desk", StaffStatus.ACTIVE, null);
        verify(staffProfileRepository, never()).searchAdmin(any(), any(), any(), any(), any());
    }

    @Test
    void summaryPassesRoleFilterToAggregate() {
        StaffAdminSummaryRow row = mock(StaffAdminSummaryRow.class);
        when(row.getTotal()).thenReturn(2L);
        when(row.getActive()).thenReturn(2L);
        when(row.getInactive()).thenReturn(0L);
        when(row.getNoAccountStaffs()).thenReturn(0L);
        when(row.getInactiveAccountStaffs()).thenReturn(1L);
        when(staffProfileRepository.summarizeAdmin(10L, "cashier", StaffStatus.ACTIVE, UserRole.CASHIER)).thenReturn(row);

        var response = service.summary(10L, " cashier ", StaffStatus.ACTIVE, UserRole.CASHIER);

        assertThat(response.getTotal()).isEqualTo(2L);
        assertThat(response.getNoAccountStaffs()).isZero();
        assertThat(response.getInactiveAccountStaffs()).isEqualTo(1L);
        verify(staffProfileRepository).summarizeAdmin(10L, "cashier", StaffStatus.ACTIVE, UserRole.CASHIER);
    }
}
