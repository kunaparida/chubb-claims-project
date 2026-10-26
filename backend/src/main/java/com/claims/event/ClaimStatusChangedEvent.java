package com.claims.event;

import com.claims.entity.ClaimStatus;
import com.claims.entity.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Integration event published whenever a claim changes status. {@code eventId} lets consumers de-duplicate,
 * because the outbox gives at-least-once delivery.
 *
 * <p>{@code claimantEmail} and {@code claimantName} are there so a consumer can tell the claimant without calling
 * back into this service (the notification service is the first). They are personal data: keep them out of logs and
 * do not add fields a consumer does not need. New fields are only ever added, never renamed, so older consumers keep working.
 */
public record ClaimStatusChangedEvent(String eventId, Long claimId, String claimNumber, ClaimType claimType,
                                      String market, BigDecimal amountClaimed, ClaimStatus fromStatus,
                                      ClaimStatus toStatus, String changedBy, Instant occurredAt,
                                      String claimantEmail, String claimantName) {}
