package com.claims.notification.config;

import com.claims.notification.event.BadEventException;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * What happens when a record cannot be turned into an email.
 * <ul>
 *   <li>A transient failure (the mail server is down): the same record is tried again after a growing wait, up to
 *       {@code notifications.retry.max-retries} times. Later records on that partition wait, which keeps the order of a claim's emails.</li>
 *   <li>Still failing, or a record that can never work ({@link BadEventException}): it is copied to the dead-letter topic with the
 *       reason in its headers, and the consumer moves on, so one bad record cannot block everyone behind it.</li>
 * </ul>
 * The dead-letter record holds the original payload, which includes the claimant's email address: treat that topic like the main one.
 */
@Configuration
public class KafkaErrorHandlingConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka, NotificationProperties properties) {
        // Partition -1: let the producer choose, so the dead-letter topic does not need as many partitions as the main one.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, exception) -> new TopicPartition(properties.deadLetterTopic(), -1));

        NotificationProperties.Retry retry = properties.retry();
        ExponentialBackOff backOff = new ExponentialBackOff(retry.initialDelayMs(), retry.multiplier());
        backOff.setMaxInterval(retry.maxDelayMs());
        backOff.setMaxAttempts(retry.maxRetries());

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(BadEventException.class);
        handler.setRetryListeners((record, exception, attempt) -> log.warn(
                "Delivery attempt {} failed for {}-{}@{}: {}", attempt, record.topic(), record.partition(), record.offset(),
                describe(exception)));
        return handler;
    }

    /**
     * The reason in one line for the log: the root cause, except that it stops at a {@link BadEventException}. That exception's message
     * is written to be safe; the JSON error underneath it can quote part of the payload, which holds an email address.
     */
    static String describe(Throwable exception) {
        Throwable cause = exception;
        while (!(cause instanceof BadEventException) && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
