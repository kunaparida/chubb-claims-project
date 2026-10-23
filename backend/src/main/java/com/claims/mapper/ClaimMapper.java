package com.claims.mapper;

import com.claims.dto.ClaimHistoryResponse;
import com.claims.dto.ClaimResponse;
import com.claims.entity.Claim;
import com.claims.entity.ClaimStatusHistory;

public final class ClaimMapper {
    private ClaimMapper() {}

    public static ClaimResponse toResponse(Claim claim) {
        return new ClaimResponse(claim.getId(), claim.getClaimNumber(), claim.getPolicyNumber(),
                claim.getClaimantName(), claim.getClaimantEmail(), claim.getIncidentDate(), claim.getDescription(),
                claim.getAmountClaimed(), claim.getClaimType(), claim.getMarket(), claim.getStatus(),
                claim.getClaimant().getId(),
                claim.getAssignedOfficer() == null ? null : claim.getAssignedOfficer().getId(),
                claim.getAssignedOfficer() == null ? null : claim.getAssignedOfficer().getFullName(),
                claim.getInformationRequest(), claim.getInformationResponse(), claim.getDecisionNotes(),
                claim.getCreatedAt(), claim.getUpdatedAt());
    }

    public static ClaimHistoryResponse toHistoryResponse(ClaimStatusHistory entry) {
        return new ClaimHistoryResponse(entry.getId(), entry.getFromStatus(), entry.getToStatus(),
                entry.getChangedBy().getId(), entry.getChangedBy().getFullName(), entry.getNote(),
                entry.getChangedAt());
    }
}
