package com.claims.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.claims.entity.OutboxEvent;
import com.claims.repository.OutboxEventRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {
    private static final String TOPIC = "claims.claim-status-changed";

    @Mock OutboxEventRepository outbox;
    @Mock EventPublisher publisher;

    @Test
    void publishesPendingEventsInOrderAndMarksThemPublished() {
        OutboxEvent first = event(1L, "{\"n\":1}");
        OutboxEvent second = event(2L, "{\"n\":2}");
        when(outbox.findTop50ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(first, second));

        int published = new OutboxPublisher(outbox, publisher, TOPIC).publishPending();

        assertEquals(2, published);
        InOrder order = inOrder(publisher);
        order.verify(publisher).publish(TOPIC, "1", "{\"n\":1}");
        order.verify(publisher).publish(TOPIC, "2", "{\"n\":2}");
        assertNotNull(first.getPublishedAt());
        assertNotNull(second.getPublishedAt());
    }

    @Test
    void stopsAtTheFirstFailureSoOrderingIsKeptAndTheRowIsRetriedLater() {
        OutboxEvent first = event(1L, "a");
        OutboxEvent second = event(2L, "b");
        when(outbox.findTop50ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("broker down")).when(publisher).publish(eq(TOPIC), eq("1"), any());

        int published = new OutboxPublisher(outbox, publisher, TOPIC).publishPending();

        assertEquals(0, published);
        assertNull(first.getPublishedAt());
        assertNull(second.getPublishedAt());
        verify(publisher, never()).publish(eq(TOPIC), eq("2"), any());
    }

    @Test
    void doesNothingWhenThereIsNothingPending() {
        when(outbox.findTop50ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of());

        assertEquals(0, new OutboxPublisher(outbox, publisher, TOPIC).publishPending());
        verify(publisher, never()).publish(any(), any(), any());
    }

    @Test
    void scheduledRunDelegatesToPublishPending() {
        OutboxEvent only = event(5L, "x");
        when(outbox.findTop50ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(only));

        new OutboxPublisher(outbox, publisher, TOPIC).scheduledPublish();

        verify(publisher).publish(TOPIC, "5", "x");
        assertNotNull(only.getPublishedAt());
    }

    private static OutboxEvent event(Long aggregateId, String payload) {
        return new OutboxEvent("Claim", aggregateId, "ClaimStatusChanged", payload);
    }
}
