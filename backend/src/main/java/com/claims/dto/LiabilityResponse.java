package com.claims.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param pendingExposure            amount claimed on claims not yet decided
 * @param approvedAwaitingSettlement amount approved but not yet paid out
 * @param byClaimType                the same two figures per line of business (MOTOR / PROPERTY)
 * @param byMarket                   the same two figures per market code
 */
public record LiabilityResponse(BigDecimal pendingExposure, BigDecimal approvedAwaitingSettlement,
                                List<ExposureLine> byClaimType, List<ExposureLine> byMarket) {

    public record ExposureLine(String key, BigDecimal pendingExposure, BigDecimal approvedAwaitingSettlement) {}
}
