package com.claims.service;

import com.claims.dto.ClaimHistoryResponse;
import com.claims.entity.Claim;
import com.claims.entity.ClaimStatus;
import com.claims.entity.ClaimStatusHistory;
import com.claims.entity.OutboxEvent;
import com.claims.entity.User;
import com.claims.event.ClaimStatusChangedEvent;
import com.claims.mapper.ClaimMapper;
import com.claims.repository.ClaimStatusHistoryRepository;
import com.claims.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records every status change twice, inside the caller's transaction: once in the audit history and once in the
 * outbox (as a {@link ClaimStatusChangedEvent}). Because both rows commit or roll back with the claim itself,
 * history and published events can never disagree with the claim's real state.
 */
@Component
public class ClaimAuditRecorder {
    public static final String AGGREGATE_TYPE = "Claim";
    public static final String EVENT_TYPE = "ClaimStatusChanged";

    private final ClaimStatusHistoryRepository history;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public ClaimAuditRecorder(ClaimStatusHistoryRepository history, OutboxEventRepository outbox,
                              ObjectMapper objectMapper) {
        this.history = history;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Claim claim, ClaimStatus from, ClaimStatus to, User actor, String note) {
        history.save(new ClaimStatusHistory(claim, from, to, actor, note));

        ClaimStatusChangedEvent event = new ClaimStatusChangedEvent(UUID.randomUUID().toString(), claim.getId(),
                claim.getClaimNumber(), claim.getClaimType(), claim.getMarket(), claim.getAmountClaimed(), from, to,
                actor.getEmail(), Instant.now(), claim.getClaimantEmail(), claim.getClaimantName());
        outbox.save(new OutboxEvent(AGGREGATE_TYPE, claim.getId(), EVENT_TYPE, toJson(event)));
    }

    @Transactional(readOnly = true)
    public List<ClaimHistoryResponse> history(Long claimId) {
        return history.findByClaimIdOrderByChangedAtAscIdAsc(claimId).stream()
                .map(ClaimMapper::toHistoryResponse)
                .toList();
    }

    private String toJson(ClaimStatusChangedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise " + EVENT_TYPE + " event", ex);
        }
    }
}
