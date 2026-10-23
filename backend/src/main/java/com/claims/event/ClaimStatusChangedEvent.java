package com.claims.event;

import com.claims.entity.ClaimStatus;
import com.claims.entity.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Integration event published whenever a claim changes status. {@code eventId} lets consumers de-duplicate,
 * because the outbox gives at-least-once delivery.
 */
public record ClaimStatusChangedEvent(String eventId, Long claimId, String claimNumber, ClaimType claimType,
                                      String market, BigDecimal amountClaimed, ClaimStatus fromStatus,
                                      ClaimStatus toStatus, String changedBy, Instant occurredAt) {}
