package com.claims.notification;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.claims.notification.mail.EmailDeliveryException;
import com.claims.notification.mail.EmailMessage;
import com.claims.notification.mail.EmailSender;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * What happens when the mail server misbehaves, with a real Kafka broker. The sender is replaced by one that fails on demand:
 * an address starting {@code flaky@} fails its first two attempts; one starting {@code down@} always fails.
 * Retries are set to 2 here, so a record that never succeeds is attempted three times.
 */
class NotificationRetryTest extends KafkaFlowTestBase {

    static class ScriptedEmailSender implements EmailSender {
        final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        final List<EmailMessage> delivered = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            int attempt = attempts.computeIfAbsent(message.eventId(), id -> new AtomicInteger()).incrementAndGet();
            if (message.toAddress().startsWith("down@")) {
                throw new EmailDeliveryException("the mail server is unreachable", null);
            }
            if (message.toAddress().startsWith("flaky@") && attempt <= 2) {
                throw new EmailDeliveryException("temporary failure, attempt " + attempt, null);
            }
            delivered.add(message);
        }

        int attemptsFor(String eventId) {
            AtomicInteger count = attempts.get(eventId);
            return count == null ? 0 : count.get();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ScriptedEmailSender scriptedEmailSender() {
            return new ScriptedEmailSender();
        }
    }

    @Autowired ScriptedEmailSender sender;

    private boolean delivered(String eventId) {
        return sender.delivered.stream().anyMatch(m -> eventId.equals(m.eventId()));
    }

    @Test
    void aTemporaryMailFailureIsRetriedUntilItWorksAndNothingIsDeadLettered() throws Exception {
        String json = TestEvents.json("retry-1", "CLM-RETRY-1", "UNDER_REVIEW", "APPROVED", "flaky@example.com", "Flaky");
        publish("1", json);

        await().atMost(Duration.ofSeconds(20)).until(() -> delivered("retry-1"));

        assertEquals(3, sender.attemptsFor("retry-1"), "two failures, then the successful attempt");
        assertEquals(1, sender.delivered.stream().filter(m -> "retry-1".equals(m.eventId())).count(), "and only one email");
        assertTrue(noDeadLetterWithin(Duration.ofSeconds(1), json::equals), "a record that worked is not dead-lettered");
    }

    @Test
    void aMailServerThatStaysDownSendsTheRecordToTheDeadLetterTopicAfterTheRetries() throws Exception {
        String json = TestEvents.json("retry-2", "CLM-RETRY-2", "UNDER_REVIEW", "APPROVED", "down@example.com", "Down");
        publish("2", json);

        var dead = awaitDeadLetter(json::equals);

        assertEquals(3, sender.attemptsFor("retry-2"), "the first attempt plus two retries");
        assertFalse(delivered("retry-2"));
        assertEquals(KafkaFlowTestBase.TOPIC, new String(dead.headers().lastHeader("kafka_dlt-original-topic").value()));
        assertNotNull(dead.headers().lastHeader("kafka_dlt-exception-message"));
    }

    @Test
    void afterAGivenUpRecordTheNextEventOnTheSamePartitionIsStillSent() throws Exception {
        publish("3", TestEvents.json("retry-3a", "CLM-RETRY-3", "UNDER_REVIEW", "APPROVED", "down@example.com", "Down"));
        publish("3", TestEvents.json("retry-3b", "CLM-RETRY-3", "APPROVED", "SETTLED", "fine@example.com", "Fine"));

        await().atMost(Duration.ofSeconds(20)).until(() -> delivered("retry-3b"));

        assertFalse(delivered("retry-3a"));
        assertEquals(3, sender.attemptsFor("retry-3a"));
    }
}
