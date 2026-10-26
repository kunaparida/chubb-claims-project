package com.claims.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Everything this service can be configured with (see {@code application.properties} for the defaults and the environment variables).
 *
 * @param topic            the topic the claims backend publishes to
 * @param deadLetterTopic  where a record goes when it cannot be handled (must exist; see DOCKER.md)
 * @param portalUrl        the address of the claims web app, used for the link in each email
 * @param dedupeCapacity   how many recent event ids are remembered to skip duplicates
 */
@ConfigurationProperties("notifications")
public record NotificationProperties(String topic, String deadLetterTopic, String portalUrl,
                                     @DefaultValue("10000") int dedupeCapacity, Mail mail, Retry retry) {

    /**
     * @param mode {@code smtp} sends through the configured SMTP server; {@code log} only writes the email to the log (no mail server needed)
     * @param from the sender shown to the claimant
     */
    public record Mail(@DefaultValue("smtp") String mode, String from) {}

    /**
     * Retries of a failed email before the record is dead-lettered: the first attempt plus {@code maxRetries} more, waiting
     * {@code initialDelayMs}, then twice as long each time (capped at {@code maxDelayMs}).
     */
    public record Retry(@DefaultValue("3") int maxRetries, @DefaultValue("1000") long initialDelayMs,
                        @DefaultValue("2.0") double multiplier, @DefaultValue("30000") long maxDelayMs) {}
}
