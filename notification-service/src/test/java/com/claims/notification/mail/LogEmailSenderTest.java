package com.claims.notification.mail;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.claims.notification.TestEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** The mode for running without a mail server: the email goes to the log, and the address is still masked. */
class LogEmailSenderTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(LogEmailSender.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void capture() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void release() {
        logger.detachAppender(appender);
    }

    private EmailMessage email() {
        return new EmailComposer(TestEvents.properties()).compose(TestEvents.event("evt-1", "UNDER_REVIEW", "APPROVED"));
    }

    @Test
    void writesTheSubjectAndTheBodyToTheLogInsteadOfSending() {
        new LogEmailSender().send(email());

        assertEquals(1, appender.list.size());
        String line = appender.list.get(0).getFormattedMessage();
        assertTrue(line.contains("not sent"));
        assertTrue(line.contains("Your claim CLM-TEST-7 has been approved"));
        assertTrue(line.contains("Amount claimed: 1,250.00 (HK)"));
    }

    @Test
    void theAddressIsMaskedEvenInThisMode() {
        new LogEmailSender().send(email());

        String line = appender.list.get(0).getFormattedMessage();
        assertTrue(line.contains("j***@example.com"));
        assertFalse(line.contains("jane.doe@example.com"));
    }
}
