package com.claims.notification;

import static org.junit.jupiter.api.Assertions.*;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The whole path, with nothing mocked: a record on a real Kafka topic, the real consumer, the real SMTP sender, and a real
 * SMTP server (GreenMail) whose mailbox the test reads.
 */
class NotificationFlowTest extends KafkaFlowTestBase {
    private static final GreenMail GREEN_MAIL = startMailServer();
    private static int smtpPort;

    private static GreenMail startMailServer() {
        try (ServerSocket socket = new ServerSocket(0)) {
            smtpPort = socket.getLocalPort();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        GreenMail server = new GreenMail(new ServerSetup(smtpPort, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        server.start();
        return server;
    }

    @DynamicPropertySource
    static void mailServer(DynamicPropertyRegistry registry) {
        registry.add("notifications.mail.mode", () -> "smtp");
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> smtpPort);
    }

    @AfterAll
    static void stopMailServer() {
        GREEN_MAIL.stop();
    }

    @BeforeEach
    void emptyTheMailbox() throws Exception {
        GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    private static MimeMessage[] awaitEmails(int count) {
        assertTrue(GREEN_MAIL.waitForIncomingEmail(20_000, count), "expected " + count + " email(s) but the mail server has "
                + GREEN_MAIL.getReceivedMessages().length);
        return GREEN_MAIL.getReceivedMessages();
    }

    private static String address(MimeMessage mail) throws Exception {
        return ((InternetAddress) mail.getRecipients(Message.RecipientType.TO)[0]).getAddress();
    }

    /** The decoded plain-text part (the raw message may be quoted-printable and wrapped). */
    private static String textOf(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String text = textOf(multipart.getBodyPart(i));
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    @Autowired KafkaListenerEndpointRegistry listeners;

    @Test
    void theConsumerGroupIsTheConfiguredOneSoRestartsResumeWhereTheyStopped() {
        assertEquals("notification-it", listeners.getListenerContainer("claim-status-notifications").getGroupId());
    }

    @Test
    void aStatusChangeOnTheTopicEmailsTheClaimant() throws Exception {
        publish("7", TestEvents.json("flow-1", "CLM-FLOW-1", "UNDER_REVIEW", "APPROVED", "alice@example.com", "Alice Tan"));

        MimeMessage[] mails = awaitEmails(1);

        assertEquals(1, mails.length);
        assertEquals("alice@example.com", address(mails[0]));
        assertEquals("Your claim CLM-FLOW-1 has been approved", mails[0].getSubject());
        String text = textOf(mails[0]);
        assertTrue(text.startsWith("Hello Alice Tan,"));
        assertTrue(text.contains(TestEvents.PORTAL + "/claims/7"));
        assertTrue(text.contains("Amount claimed: 1,250.00 (HK)"));
    }

    @Test
    void aClaimsEventsAreEmailedInTheOrderTheyHappened() throws Exception {
        publish("7", TestEvents.json("flow-2a", "CLM-FLOW-2", null, "SUBMITTED", "bob@example.com", "Bob Lim"));
        publish("7", TestEvents.json("flow-2b", "CLM-FLOW-2", "SUBMITTED", "UNDER_REVIEW", "bob@example.com", "Bob Lim"));
        publish("7", TestEvents.json("flow-2c", "CLM-FLOW-2", "UNDER_REVIEW", "MORE_INFO_REQUIRED", "bob@example.com", "Bob Lim"));

        MimeMessage[] mails = awaitEmails(3);

        assertEquals(3, mails.length);
        assertEquals("We have received your claim CLM-FLOW-2", mails[0].getSubject());
        assertEquals("Your claim CLM-FLOW-2 is now under review", mails[1].getSubject());
        assertEquals("Action needed: more information for claim CLM-FLOW-2", mails[2].getSubject());
    }

    @Test
    void aRedeliveredEventIsEmailedOnlyOnce() throws Exception {
        String first = TestEvents.json("flow-3a", "CLM-FLOW-3", "SUBMITTED", "UNDER_REVIEW", "carol@example.com", "Carol");
        publish("7", first);
        publish("7", first); // the backend's outbox delivers at least once, so a repeat is expected sometimes
        publish("7", TestEvents.json("flow-3b", "CLM-FLOW-3", "UNDER_REVIEW", "APPROVED", "carol@example.com", "Carol"));

        MimeMessage[] mails = awaitEmails(2);

        // The events share a key, so they are handled in order: once the third one has been emailed the repeat has already been skipped.
        assertEquals(2, mails.length);
        assertEquals("Your claim CLM-FLOW-3 is now under review", mails[0].getSubject());
        assertEquals("Your claim CLM-FLOW-3 has been approved", mails[1].getSubject());
        assertEquals(2, GREEN_MAIL.getReceivedMessages().length);
    }

    @Test
    void aRecordThatIsNotAnEventGoesToTheDeadLetterTopicAndDoesNotBlockTheNextOne() throws Exception {
        String poison = "this is not json {";
        publish("7", poison);
        publish("7", TestEvents.json("flow-4", "CLM-FLOW-4", "SUBMITTED", "UNDER_REVIEW", "dave@example.com", "Dave"));

        MimeMessage[] mails = awaitEmails(1);

        assertEquals("dave@example.com", address(mails[0]));
        ConsumerRecord<String, String> dead = awaitDeadLetter(poison::equals);
        assertEquals("7", dead.key());
        assertNotNull(dead.headers().lastHeader("kafka_dlt-exception-message"), "the reason travels with the record");
        assertNotNull(dead.headers().lastHeader("kafka_dlt-original-topic"));
    }

    @Test
    void anEventWithNoEmailAddressIsDeadLetteredAndNothingIsSent() throws Exception {
        String noAddress = TestEvents.json("flow-5a", "CLM-FLOW-5", "SUBMITTED", "UNDER_REVIEW", null, "Nobody");
        publish("7", noAddress);
        publish("7", TestEvents.json("flow-5b", "CLM-FLOW-5", "UNDER_REVIEW", "APPROVED", "erin@example.com", "Erin"));

        MimeMessage[] mails = awaitEmails(1);

        assertEquals("erin@example.com", address(mails[0]));
        assertNotNull(awaitDeadLetter(noAddress::equals));
        assertEquals(1, GREEN_MAIL.getReceivedMessages().length);
    }

    @Test
    void theDeadLetterRecordKeepsTheOriginalPayloadByteForByte() throws Exception {
        String poison = "{\"eventId\":\"flow-6\",\"note\":\"ünïcödé\"";
        publish("9", poison);

        ConsumerRecord<String, String> dead = awaitDeadLetter(poison::equals);

        assertArrayEquals(poison.getBytes(StandardCharsets.UTF_8), dead.value().getBytes(StandardCharsets.UTF_8));
        assertTrue(Arrays.stream(dead.headers().toArray()).anyMatch(h -> h.key().startsWith("kafka_dlt-")));
        assertTrue(noDeadLetterWithin(Duration.ofMillis(300), v -> v.contains("never published")));
    }
}
