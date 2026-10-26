package com.claims.notification.mail;

import static org.junit.jupiter.api.Assertions.*;

import com.claims.notification.TestEvents;
import com.claims.notification.event.BadEventException;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Real SMTP: the message is sent to GreenMail, a small SMTP server running inside the test, and read back from its mailbox. */
class SmtpEmailSenderTest {
    private GreenMail greenMail;
    private SmtpEmailSender sender;
    private final EmailComposer composer = new EmailComposer(TestEvents.properties());

    @BeforeEach
    void startMailServer() throws Exception {
        int port = freePort();
        greenMail = new GreenMail(new ServerSetup(port, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        greenMail.start();
        sender = new SmtpEmailSender(mailSenderOn(port), "Claims Platform <no-reply@claims.local>");
    }

    @AfterEach
    void stopMailServer() {
        greenMail.stop();
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static JavaMailSenderImpl mailSenderOn(int port) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("127.0.0.1");
        mailSender.setPort(port);
        mailSender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "2000");
        mailSender.getJavaMailProperties().put("mail.smtp.timeout", "2000");
        return mailSender;
    }

    private MimeMessage onlyMessage() {
        assertTrue(greenMail.waitForIncomingEmail(5000, 1), "the mail server received nothing");
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertEquals(1, received.length);
        return received[0];
    }

    @Test
    void deliversTheEmailToTheClaimantThroughSmtp() throws Exception {
        sender.send(composer.compose(TestEvents.event("evt-1", "UNDER_REVIEW", "APPROVED")));

        MimeMessage mail = onlyMessage();
        InternetAddress to = (InternetAddress) mail.getRecipients(Message.RecipientType.TO)[0];
        assertEquals("jane.doe@example.com", to.getAddress());
        assertEquals("Jane Doe", to.getPersonal());
        assertEquals("Your claim CLM-TEST-7 has been approved", mail.getSubject());
        assertEquals("no-reply@claims.local", ((InternetAddress) mail.getFrom()[0]).getAddress());
        assertEquals("Claims Platform", ((InternetAddress) mail.getFrom()[0]).getPersonal());
    }

    @Test
    void carriesBothAPlainTextAndAnHtmlPartAndTheAutomaticMessageHeaders() throws Exception {
        sender.send(composer.compose(TestEvents.event("evt-1", "UNDER_REVIEW", "APPROVED")));

        MimeMessage mail = onlyMessage();
        String whole = GreenMailUtil.getWholeMessage(mail);
        assertTrue(whole.contains("Content-Type: text/plain"), "plain-text part");
        assertTrue(whole.contains("Content-Type: text/html"), "HTML part");
        assertArrayEquals(new String[] {"auto-generated"}, mail.getHeader("Auto-Submitted"));
        assertArrayEquals(new String[] {"evt-1"}, mail.getHeader("X-Claims-Event-Id"));
    }

    @Test
    void keepsNonAsciiNamesIntact() throws Exception {
        var event = TestEvents.event("evt-1", null, "SUBMITTED");
        event = new com.claims.notification.event.ClaimStatusChanged(event.eventId(), event.claimId(), event.claimNumber(),
                event.claimType(), event.market(), event.amountClaimed(), null, "SUBMITTED", event.changedBy(), event.occurredAt(),
                event.claimantEmail(), "Zoë Müller 李");

        sender.send(composer.compose(event));

        InternetAddress to = (InternetAddress) onlyMessage().getRecipients(Message.RecipientType.TO)[0];
        assertEquals("Zoë Müller 李", to.getPersonal());
    }

    @Test
    void anAddressThatIsNotValidIsNotRetriedBecauseNoRetryCanFixIt() {
        EmailMessage bad = new EmailMessage("evt-1", "not an address", "Jane", "subject", "text", "<p>html</p>");

        assertThrows(BadEventException.class, () -> sender.send(bad));
        assertEquals(0, greenMail.getReceivedMessages().length);
    }

    @Test
    void aMailServerThatIsDownIsADeliveryFailureSoTheEventIsRetried() throws Exception {
        greenMail.stop();

        EmailDeliveryException error = assertThrows(EmailDeliveryException.class,
                () -> sender.send(composer.compose(TestEvents.event("evt-1", "UNDER_REVIEW", "APPROVED"))));

        assertTrue(error.getMessage().contains("evt-1"));
        assertFalse(error.getMessage().contains("jane.doe@"), "the address is masked in the message");
        assertTrue(error.getMessage().contains("j***@example.com"));
    }
}
