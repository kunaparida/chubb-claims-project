package com.claims.notification.mail;

/** One email, ready to send. {@code eventId} is carried only so the message can be traced back to its event. */
public record EmailMessage(String eventId, String toAddress, String toName, String subject, String text, String html) {}
