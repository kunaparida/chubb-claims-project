package com.claims.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.claims.dto.LiabilityResponse;
import com.claims.dto.LiabilityResponse.ExposureLine;
import com.claims.entity.ClaimStatus;
import com.claims.entity.ClaimType;
import com.claims.repository.ClaimRepository;
import com.claims.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiabilityBreakdownTest {
    private final ClaimRepository claims = mock(ClaimRepository.class);
    private final ManagerDashboardService service = new ManagerDashboardService(claims, mock(UserRepository.class));

    @Test
    void splitsExposureByClaimTypeAndMarketIntoPendingAndApproved() {
        List<ClaimStatus> statuses = List.of(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW,
                ClaimStatus.MORE_INFO_REQUIRED, ClaimStatus.APPROVED);
        when(claims.sumAmountByClaimTypeAndStatus(statuses)).thenReturn(List.of(
                new Object[] {ClaimType.PROPERTY, ClaimStatus.UNDER_REVIEW, new BigDecimal("200.00")},
                new Object[] {ClaimType.MOTOR, ClaimStatus.SUBMITTED, new BigDecimal("100.00")},
                new Object[] {ClaimType.MOTOR, ClaimStatus.UNDER_REVIEW, new BigDecimal("50.00")},
                new Object[] {ClaimType.MOTOR, ClaimStatus.APPROVED, new BigDecimal("80.00")}));
        when(claims.sumAmountByMarketAndStatus(statuses)).thenReturn(List.<Object[]>of(
                new Object[] {"SG", ClaimStatus.APPROVED, new BigDecimal("80.00")},
                new Object[] {"HK", ClaimStatus.MORE_INFO_REQUIRED, new BigDecimal("25.50")}));

        LiabilityResponse result = service.liability();

        assertEquals(2, result.byClaimType().size());
        ExposureLine motor = result.byClaimType().get(0);
        assertEquals("MOTOR", motor.key());
        assertEquals(new BigDecimal("150.00"), motor.pendingExposure());
        assertEquals(new BigDecimal("80.00"), motor.approvedAwaitingSettlement());
        ExposureLine property = result.byClaimType().get(1);
        assertEquals("PROPERTY", property.key());
        assertEquals(new BigDecimal("200.00"), property.pendingExposure());
        assertEquals(BigDecimal.ZERO, property.approvedAwaitingSettlement());

        assertEquals(List.of("HK", "SG"), result.byMarket().stream().map(ExposureLine::key).toList());
        assertEquals(new BigDecimal("25.50"), result.byMarket().get(0).pendingExposure());
        assertEquals(new BigDecimal("80.00"), result.byMarket().get(1).approvedAwaitingSettlement());
    }

    @Test
    void breakdownIsEmptyWhenThereAreNoClaims() {
        LiabilityResponse result = service.liability();

        assertTrue(result.byClaimType().isEmpty());
        assertTrue(result.byMarket().isEmpty());
    }
}
