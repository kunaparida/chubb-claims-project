package com.claims.notification.config;

import static org.junit.jupiter.api.Assertions.*;

import com.claims.notification.TestEvents;
import com.claims.notification.mail.LogEmailSender;
import com.claims.notification.mail.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class MailConfigTest {
    private final MailConfig config = new MailConfig();

    private static NotificationProperties withMode(String mode) {
        NotificationProperties base = TestEvents.properties();
        return new NotificationProperties(base.topic(), base.deadLetterTopic(), base.portalUrl(), base.dedupeCapacity(),
                new NotificationProperties.Mail(mode, "Claims <no-reply@claims.local>"), base.retry());
    }

    private static ObjectProvider<JavaMailSender> noMailServer() {
        return new StaticListableBeanFactory().getBeanProvider(JavaMailSender.class);
    }

    private static ObjectProvider<JavaMailSender> mailServer() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("mailSender", new JavaMailSenderImpl());
        return beans.getBeanProvider(JavaMailSender.class);
    }

    @Test
    void smtpModeSendsThroughTheMailServer() {
        assertInstanceOf(SmtpEmailSender.class, config.emailSender(withMode("smtp"), mailServer()));
    }

    @Test
    void logModeNeedsNoMailServer() {
        assertInstanceOf(LogEmailSender.class, config.emailSender(withMode("log"), noMailServer()));
    }

    @Test
    void theModeIsNotCaseSensitive() {
        assertInstanceOf(LogEmailSender.class, config.emailSender(withMode(" LOG "), noMailServer()));
    }

    @Test
    void smtpModeWithoutAMailServerStopsStartupAndSaysWhy() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> config.emailSender(withMode("smtp"), noMailServer()));
        assertTrue(error.getMessage().contains("MAIL_HOST"));
    }

    @Test
    void anUnknownModeStopsStartupAndSaysWhich() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> config.emailSender(withMode("smpt"), mailServer()));
        assertTrue(error.getMessage().contains("'smtp' or 'log'"));
        assertTrue(error.getMessage().contains("smpt"));
    }
}
