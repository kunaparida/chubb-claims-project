package com.claims.dto;

import com.claims.entity.ClaimStatus;
import java.time.Instant;

public record ClaimHistoryResponse(Long id, ClaimStatus fromStatus, ClaimStatus toStatus, Long changedById,
                                   String changedByName, String note, Instant changedAt) {}
