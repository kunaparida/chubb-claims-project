package com.claims.dto;

public record OfficerPerformance(Long officerId, String officerName, long assignedClaims,
                                 long activeClaims, long resolvedClaims) {}
