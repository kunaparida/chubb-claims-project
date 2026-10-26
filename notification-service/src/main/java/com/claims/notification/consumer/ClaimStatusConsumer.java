package com.claims.notification.consumer;

import com.claims.notification.event.EventParser;
import com.claims.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads claim status events. Anything that throws here is handled by the error handler in
 * {@code KafkaErrorHandlingConfig}: a failed email is retried with a growing wait and then dead-lettered; a record that
 * cannot be understood is dead-lettered at once. A record's offset is committed only after this method returns.
 */
@Component
public class ClaimStatusConsumer {
    private static final Logger log = LoggerFactory.getLogger(ClaimStatusConsumer.class);

    private final EventParser parser;
    private final NotificationService notifications;

    public ClaimStatusConsumer(EventParser parser, NotificationService notifications) {
        this.parser = parser;
        this.notifications = notifications;
    }

    /**
     * {@code idIsGroup = false}: by default Spring Kafka uses a listener's id as the consumer group, which would silently ignore
     * {@code spring.kafka.consumer.group-id} (NOTIFICATIONS_GROUP_ID). The group decides where the service resumes after a restart.
     */
    /**
     * {@code idIsGroup = false}: by default Spring Kafka uses a listener's id as the consumer group, which would silently ignore
     * {@code spring.kafka.consumer.group-id} (NOTIFICATIONS_GROUP_ID). The group decides where the service resumes after a restart.
     */
    @KafkaListener(id = "claim-status-notifications", idIsGroup = false, groupId = "${spring.kafka.consumer.group-id}",
            topics = "${notifications.topic}")
    public void onMessage(ConsumerRecord<String, String> record) {
        log.debug("Received {}-{}@{} (key {})", record.topic(), record.partition(), record.offset(), record.key());
        notifications.handle(parser.parse(record.value()));
    }
}
