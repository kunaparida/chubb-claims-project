package com.claims.notification.mail;

/** The email could not be handed to the mail server. Worth retrying: the server may be down for a moment. */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
