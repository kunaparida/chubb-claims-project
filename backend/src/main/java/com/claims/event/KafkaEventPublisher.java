package com.claims.event;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes to Kafka (enable with {@code app.events.publisher=kafka} and {@code spring.kafka.bootstrap-servers}).
 * Waits for the broker acknowledgement so the outbox row is only marked published after a successful send.
 * The claim id is the record key, which keeps all events of one claim ordered inside one partition.
 */
@Component
@ConditionalOnProperty(name = "app.events.publisher", havingValue = "kafka")
public class KafkaEventPublisher implements EventPublisher {
    private static final long ACK_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafka;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publish(String topic, String key, String payload) {
        try {
            kafka.send(topic, key, payload).get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing to Kafka", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Kafka publish failed for topic " + topic, ex);
        }
    }
}
