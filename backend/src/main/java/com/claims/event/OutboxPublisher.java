package com.claims.event;

import com.claims.entity.OutboxEvent;
import com.claims.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays unpublished outbox rows to the broker, oldest first. Delivery is at-least-once: if the process dies after
 * a send but before the commit, the row is sent again, so consumers must de-duplicate on {@code eventId}.
 * A failure stops the batch so ordering is preserved and the same row is retried on the next poll.
 */
@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outbox;
    private final EventPublisher publisher;
    private final String topic;

    public OutboxPublisher(OutboxEventRepository outbox, EventPublisher publisher,
                           @Value("${app.events.topic:claims.claim-status-changed}") String topic) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:5000}")
    @Transactional
    public void scheduledPublish() {
        publishPending();
    }

    /** @return number of events published in this run */
    @Transactional
    public int publishPending() {
        List<OutboxEvent> pending = outbox.findTop50ByPublishedAtIsNullOrderByIdAsc();
        int published = 0;
        for (OutboxEvent event : pending) {
            try {
                publisher.publish(topic, String.valueOf(event.getAggregateId()), event.getPayload());
                event.markPublished(Instant.now());
                published++;
            } catch (RuntimeException ex) {
                log.warn("Publishing outbox event {} failed, will retry: {}", event.getId(), ex.getMessage());
                break;
            }
        }
        return published;
    }
}
