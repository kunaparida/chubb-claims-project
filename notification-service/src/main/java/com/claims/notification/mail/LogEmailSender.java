package com.claims.notification.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For running without a mail server ({@code notifications.mail.mode=log}): writes the email to the log instead of sending it.
 * The text includes the claimant's name and claim details, so this mode is for local use only.
 */
public class LogEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("EMAIL (log mode, not sent) to={} subject=\"{}\"\n{}", Masking.email(message.toAddress()), message.subject(),
                message.text());
    }
}
