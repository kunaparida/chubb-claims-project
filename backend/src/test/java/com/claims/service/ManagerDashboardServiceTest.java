package com.claims.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.claims.entity.ClaimStatus;
import com.claims.entity.Role;
import com.claims.entity.User;
import com.claims.exception.BusinessException;
import com.claims.repository.ClaimRepository;
import com.claims.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ManagerDashboardServiceTest {
    private final ClaimRepository claims = mock(ClaimRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ManagerDashboardService service = new ManagerDashboardService(claims, users);

    @Test
    void calculatesOpenWorkloadAndUnassignedClaims() {
        when(claims.countByStatus(ClaimStatus.SUBMITTED)).thenReturn(2L);
        when(claims.countByStatus(ClaimStatus.UNDER_REVIEW)).thenReturn(3L);
        when(claims.countByStatus(ClaimStatus.MORE_INFO_REQUIRED)).thenReturn(1L);
        when(claims.countByStatus(ClaimStatus.APPROVED)).thenReturn(4L);
        when(claims.countByStatus(ClaimStatus.REJECTED)).thenReturn(5L);
        when(claims.countByStatus(ClaimStatus.SETTLED)).thenReturn(6L);
        when(claims.countByAssignedOfficerIsNull()).thenReturn(2L);

        var result = service.workload();

        assertEquals(2, result.unassignedClaims());
        assertEquals(10, result.totalOpenClaims());
        assertEquals(2, result.submitted());
        assertEquals(3, result.underReview());
        assertEquals(1, result.awaitingInformation());
        assertEquals(4, result.approved());
        assertEquals(5, result.rejected());
        assertEquals(6, result.settled());
    }

    @Test
    void calculatesLiabilityForPendingAndApprovedClaims() {
        when(claims.sumAmountByStatusIn(List.of(
                ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.MORE_INFO_REQUIRED)))
                .thenReturn(new BigDecimal("125.50"));
        when(claims.sumAmountByStatusIn(List.of(ClaimStatus.APPROVED))).thenReturn(new BigDecimal("80.00"));

        var result = service.liability();

        assertEquals(new BigDecimal("125.50"), result.pendingExposure());
        assertEquals(new BigDecimal("80.00"), result.approvedAwaitingSettlement());
    }

    @Test
    void calculatesOfficerAssignedActiveAndResolvedClaims() {
        User officer = new User("officer@example.com", "encoded", "Officer One", Role.OFFICER);
        ReflectionTestUtils.setField(officer, "id", 22L);
        when(users.findByRole(Role.OFFICER)).thenReturn(List.of(officer));
        when(claims.countByAssignedOfficerId(22L)).thenReturn(5L);
        when(claims.countByAssignedOfficerIdAndStatusIn(eq(22L), anyList())).thenReturn(3L);

        var results = service.performance();

        assertEquals(1, results.size());
        assertEquals(22L, results.get(0).officerId());
        assertEquals("Officer One", results.get(0).officerName());
        assertEquals(5, results.get(0).assignedClaims());
        assertEquals(3, results.get(0).activeClaims());
        assertEquals(2, results.get(0).resolvedClaims());
    }

    @Test
    void onlyAllowsManagerRoleForManagerGuard() {
        service.requireManager("MANAGER");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.requireManager("OFFICER"));

        assertEquals(403, exception.getStatus().value());
    }
}
