package com.claims.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Tells claimants by email when their claim changes status. It owns no data: it reads {@code ClaimStatusChanged}
 * events from Kafka, which the claims backend writes through its transactional outbox, and sends one email per event.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
