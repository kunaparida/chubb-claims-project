package com.claims.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class EventPublishersTest {

    @Test
    void loggingPublisherNeverFails() {
        assertDoesNotThrow(() -> new LoggingEventPublisher().publish("topic", "key", "{}"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaPublisherSendsWithTheClaimIdAsKeyAndWaitsForTheAck() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send("topic", "42", "{}")).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        new KafkaEventPublisher(kafka).publish("topic", "42", "{}");

        verify(kafka).send("topic", "42", "{}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaPublisherReportsBrokerFailureSoTheOutboxRowIsRetried() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send("topic", "42", "{}"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("no broker")));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new KafkaEventPublisher(kafka).publish("topic", "42", "{}"));

        assertTrue(error.getMessage().contains("topic"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaPublisherRestoresTheInterruptFlag() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send("topic", "42", "{}")).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        try {
            assertThrows(IllegalStateException.class,
                    () -> new KafkaEventPublisher(kafka).publish("topic", "42", "{}"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted(); // clear the flag so other tests are unaffected
        }
    }
}
