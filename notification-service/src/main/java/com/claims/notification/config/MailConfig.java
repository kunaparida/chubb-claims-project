package com.claims.notification.config;

import com.claims.notification.mail.EmailSender;
import com.claims.notification.mail.LogEmailSender;
import com.claims.notification.mail.SmtpEmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** Chooses how emails leave: through SMTP, or only into the log. A wrong setting stops the service at start-up with a clear message. */
@Configuration
public class MailConfig {

    @Bean
    EmailSender emailSender(NotificationProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        String mode = properties.mail().mode() == null ? "" : properties.mail().mode().trim().toLowerCase();
        return switch (mode) {
            case "smtp" -> new SmtpEmailSender(
                    mailSender.getIfAvailable(() -> {
                        throw new IllegalStateException("notifications.mail.mode=smtp needs an SMTP server: set spring.mail.host (MAIL_HOST)");
                    }),
                    properties.mail().from());
            case "log" -> new LogEmailSender();
            default -> throw new IllegalStateException(
                    "notifications.mail.mode must be 'smtp' or 'log', not '" + properties.mail().mode() + "'");
        };
    }
}
