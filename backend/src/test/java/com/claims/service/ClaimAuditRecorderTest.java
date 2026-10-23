package com.claims.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claims.dto.ClaimHistoryResponse;
import com.claims.entity.Claim;
import com.claims.entity.ClaimStatus;
import com.claims.entity.ClaimStatusHistory;
import com.claims.entity.ClaimType;
import com.claims.entity.OutboxEvent;
import com.claims.entity.Role;
import com.claims.entity.User;
import com.claims.event.ClaimStatusChangedEvent;
import com.claims.repository.ClaimStatusHistoryRepository;
import com.claims.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClaimAuditRecorderTest {
    @Mock ClaimStatusHistoryRepository history;
    @Mock OutboxEventRepository outbox;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void recordsHistoryAndAnOutboxEventForTheSameChange() throws Exception {
        ClaimAuditRecorder recorder = new ClaimAuditRecorder(history, outbox, objectMapper);
        User officer = new User("officer@claims.local", "encoded", "Officer One", Role.OFFICER);
        Claim claim = claim(officer);

        recorder.record(claim, ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, officer, "Within cover");

        ArgumentCaptor<ClaimStatusHistory> historyCaptor = ArgumentCaptor.forClass(ClaimStatusHistory.class);
        verify(history).save(historyCaptor.capture());
        assertSame(claim, historyCaptor.getValue().getClaim());
        assertEquals(ClaimStatus.UNDER_REVIEW, historyCaptor.getValue().getFromStatus());
        assertEquals(ClaimStatus.APPROVED, historyCaptor.getValue().getToStatus());
        assertEquals("Within cover", historyCaptor.getValue().getNote());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(outboxCaptor.capture());
        OutboxEvent saved = outboxCaptor.getValue();
        assertEquals("Claim", saved.getAggregateType());
        assertEquals(42L, saved.getAggregateId());
        assertEquals("ClaimStatusChanged", saved.getEventType());
        assertNull(saved.getPublishedAt());

        ClaimStatusChangedEvent event = objectMapper.readValue(saved.getPayload(), ClaimStatusChangedEvent.class);
        assertEquals(42L, event.claimId());
        assertEquals("CLM-TEST", event.claimNumber());
        assertEquals(ClaimType.PROPERTY, event.claimType());
        assertEquals("HK", event.market());
        assertEquals(0, new BigDecimal("750.00").compareTo(event.amountClaimed()));
        assertEquals(ClaimStatus.UNDER_REVIEW, event.fromStatus());
        assertEquals(ClaimStatus.APPROVED, event.toStatus());
        assertEquals("officer@claims.local", event.changedBy());
        assertNotNull(event.eventId());
        assertNotNull(event.occurredAt());
    }

    @Test
    void initialSubmissionHasNoFromStatus() throws Exception {
        ClaimAuditRecorder recorder = new ClaimAuditRecorder(history, outbox, objectMapper);
        User claimant = new User("jane@example.com", "encoded", "Jane", Role.CLAIMANT);

        recorder.record(claim(claimant), null, ClaimStatus.SUBMITTED, claimant, "Claim submitted");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captor.capture());
        ClaimStatusChangedEvent event = objectMapper.readValue(captor.getValue().getPayload(),
                ClaimStatusChangedEvent.class);
        assertNull(event.fromStatus());
        assertEquals(ClaimStatus.SUBMITTED, event.toStatus());
    }

    @Test
    void historyIsMappedForTheApi() {
        ClaimAuditRecorder recorder = new ClaimAuditRecorder(history, outbox, objectMapper);
        User officer = new User("officer@claims.local", "encoded", "Officer One", Role.OFFICER);
        ReflectionTestUtils.setField(officer, "id", 3L);
        ClaimStatusHistory entry = new ClaimStatusHistory(claim(officer), ClaimStatus.SUBMITTED,
                ClaimStatus.UNDER_REVIEW, officer, null);
        when(history.findByClaimIdOrderByChangedAtAscIdAsc(42L)).thenReturn(List.of(entry));

        List<ClaimHistoryResponse> result = recorder.history(42L);

        assertEquals(1, result.size());
        assertEquals(ClaimStatus.UNDER_REVIEW, result.get(0).toStatus());
        assertEquals(3L, result.get(0).changedById());
        assertEquals("Officer One", result.get(0).changedByName());
    }

    @Test
    void failsLoudlyWhenThePayloadCannotBeSerialised() {
        ObjectMapper broken = new ObjectMapper(); // no JavaTimeModule: Instant cannot be written
        ClaimAuditRecorder recorder = new ClaimAuditRecorder(history, outbox, broken);
        User officer = new User("officer@claims.local", "encoded", "Officer One", Role.OFFICER);

        assertThrows(IllegalStateException.class,
                () -> recorder.record(claim(officer), ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, officer, null));
    }

    private Claim claim(User claimant) {
        Claim claim = new Claim("CLM-TEST", "POL-1", "Jane", "jane@example.com", LocalDate.now(),
                "Water damage from a burst pipe in the kitchen.", new BigDecimal("750.00"), ClaimType.PROPERTY, "HK",
                claimant);
        ReflectionTestUtils.setField(claim, "id", 42L);
        return claim;
    }
}
