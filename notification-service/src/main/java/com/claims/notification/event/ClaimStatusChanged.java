package com.claims.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * This service's own reading of the backend's {@code ClaimStatusChanged} event (the contract is the JSON, not a shared class).
 * Statuses and the claim type stay plain text, so a status added to the backend later still arrives instead of failing here, and
 * fields this service does not know are ignored.
 *
 * <p>{@code claimantEmail} and {@code claimantName} are personal data: never write them to logs unmasked.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimStatusChanged(String eventId, Long claimId, String claimNumber, String claimType, String market,
                                 BigDecimal amountClaimed, String fromStatus, String toStatus, String changedBy,
                                 Instant occurredAt, String claimantEmail, String claimantName) {}
