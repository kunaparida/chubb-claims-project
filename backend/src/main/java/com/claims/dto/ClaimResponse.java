package com.claims.dto;

import com.claims.entity.ClaimStatus;
import com.claims.entity.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;

public record ClaimResponse(Long id, String claimNumber, String policyNumber, String claimantName,
                            String claimantEmail, java.time.LocalDate incidentDate, String description,
                            BigDecimal amountClaimed, ClaimType claimType, String market, ClaimStatus status,
                            Long claimantId, Long assignedOfficerId, String assignedOfficerName,
                            String informationRequest, String informationResponse, String decisionNotes,
                            Instant createdAt, Instant updatedAt) {}
