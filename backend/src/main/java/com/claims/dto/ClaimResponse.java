package com.claims.dto;

import com.claims.entity.ClaimStatus;
import com.claims.entity.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "A claim as seen by the caller")
public record ClaimResponse(Long id,
                            @Schema(description = "Human-readable reference, unique", example = "CLM-8D45968B-EDE") String claimNumber,
                            String policyNumber, String claimantName,
                            String claimantEmail, java.time.LocalDate incidentDate, String description,
                            BigDecimal amountClaimed, ClaimType claimType,
                            @Schema(description = "Two-letter market code", example = "SG") String market,
                            @Schema(description = "SUBMITTED, UNDER_REVIEW, MORE_INFO_REQUIRED, APPROVED, REJECTED or SETTLED") ClaimStatus status,
                            Long claimantId,
                            @Schema(description = "Null until an officer picks the claim up or a manager assigns it") Long assignedOfficerId,
                            String assignedOfficerName,
                            @Schema(description = "The officer's question while the claim is MORE_INFO_REQUIRED") String informationRequest,
                            @Schema(description = "The claimant's answer") String informationResponse,
                            @Schema(description = "The latest decision note; settling replaces the approval note (the history keeps both)") String decisionNotes,
                            Instant createdAt, Instant updatedAt) {}
