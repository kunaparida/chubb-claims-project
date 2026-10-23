package com.claims.dto;

public record WorkloadResponse(long unassignedClaims, long totalOpenClaims, long submitted, long underReview,
                              long awaitingInformation, long approved, long rejected, long settled) {}
