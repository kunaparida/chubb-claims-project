package com.claims.notification.mail;

/** Delivers an email. Throws {@link EmailDeliveryException} if it could not be handed over, so the event is retried. */
public interface EmailSender {
    void send(EmailMessage message);
}
