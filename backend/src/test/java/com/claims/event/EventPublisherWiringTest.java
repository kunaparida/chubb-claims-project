package com.claims.event;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Proves the publisher is chosen by configuration: no broker needed locally, Kafka when switched on. */
class EventPublisherWiringTest {

    @Nested
    @SpringBootTest(properties = "app.outbox.poll-ms=3600000")
    class DefaultConfiguration {
        @Autowired EventPublisher publisher;

        @Test
        void usesTheLoggingPublisherSoTheAppRunsWithoutKafka() {
            assertInstanceOf(LoggingEventPublisher.class, publisher);
        }
    }

    @Nested
    @SpringBootTest(properties = {"app.events.publisher=kafka", "app.outbox.poll-ms=3600000",
            "spring.kafka.bootstrap-servers=localhost:1"})
    class KafkaConfiguration {
        @Autowired EventPublisher publisher;

        @Test
        void usesTheKafkaPublisherWhenEnabled() {
            assertInstanceOf(KafkaEventPublisher.class, publisher);
        }
    }
}
