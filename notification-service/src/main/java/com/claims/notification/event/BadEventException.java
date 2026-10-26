package com.claims.notification.event;

/**
 * The record on the topic cannot be turned into a notification, and trying again will not change that (broken JSON, a required
 * field missing). It is never retried: the record goes straight to the dead-letter topic for a person to look at.
 */
public class BadEventException extends RuntimeException {
    public BadEventException(String message) {
        super(message);
    }

    public BadEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
