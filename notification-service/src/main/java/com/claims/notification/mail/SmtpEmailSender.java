package com.claims.notification.mail;

import com.claims.notification.event.BadEventException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Sends through the SMTP server in {@code spring.mail.*}. Each message carries a plain-text and an HTML part, and the header
 * {@code Auto-Submitted: auto-generated} (RFC 3834), which tells mail systems not to answer it with an out-of-office reply.
 */
public class SmtpEmailSender implements EmailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            InternetAddress to = new InternetAddress(message.toAddress(), true);
            if (message.toName() != null && !message.toName().isBlank()) {
                to.setPersonal(message.toName(), "UTF-8");
            }

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(InternetAddress.parse(from, true)[0]);
            helper.setTo(to);
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mime.setHeader("Auto-Submitted", "auto-generated");
            if (message.eventId() != null) {
                mime.setHeader("X-Claims-Event-Id", message.eventId().replaceAll("[^A-Za-z0-9-]", ""));
            }
            mailSender.send(mime);
        } catch (AddressException ex) {
            // The address itself is wrong, which no retry can fix.
            throw new BadEventException("The claimant's email address is not valid (event " + message.eventId() + ")", ex);
        } catch (MessagingException | MailException | UnsupportedEncodingException ex) {
            throw new EmailDeliveryException(
                    "Could not send the email for event " + message.eventId() + " to " + Masking.email(message.toAddress()), ex);
        }
    }
}
