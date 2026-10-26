package com.claims.notification.service;

import com.claims.notification.event.ClaimStatusChanged;
import com.claims.notification.mail.EmailComposer;
import com.claims.notification.mail.EmailMessage;
import com.claims.notification.mail.EmailSender;
import com.claims.notification.mail.Masking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One event in, at most one email out. The event is marked as handled only <em>after</em> the email was handed to the mail
 * server: if sending fails the exception goes back to the Kafka listener, which retries and eventually dead-letters the record.
 * The cost of that order is that a crash between sending and marking can send the same email twice (at-least-once, the same
 * guarantee the backend's outbox gives).
 */
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final EmailComposer composer;
    private final EmailSender sender;
    private final ProcessedEvents processed;

    public NotificationService(EmailComposer composer, EmailSender sender, ProcessedEvents processed) {
        this.composer = composer;
        this.sender = sender;
        this.processed = processed;
    }

    public void handle(ClaimStatusChanged event) {
        if (processed.contains(event.eventId())) {
            log.info("Event {} was already handled; skipping the duplicate (claim {})", event.eventId(), event.claimNumber());
            return;
        }
        EmailMessage email = composer.compose(event);
        sender.send(email);
        processed.add(event.eventId());
        log.info("Told {} that claim {} moved {} -> {} (event {})", Masking.email(event.claimantEmail()), event.claimNumber(),
                event.fromStatus(), event.toStatus(), event.eventId());
    }
}
