package com.claims.notification.mail;

import static org.junit.jupiter.api.Assertions.*;

import com.claims.notification.TestEvents;
import com.claims.notification.config.NotificationProperties;
import com.claims.notification.event.ClaimStatusChanged;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EmailComposerTest {
    private final EmailComposer composer = new EmailComposer(TestEvents.properties());

    private static ClaimStatusChanged event(String from, String to) {
        return TestEvents.event("evt-1", from, to);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "NULL,                SUBMITTED,          We have received your claim CLM-TEST-7",
            "SUBMITTED,           UNDER_REVIEW,       Your claim CLM-TEST-7 is now under review",
            "MORE_INFO_REQUIRED,  UNDER_REVIEW,       Thank you: claim CLM-TEST-7 is back under review",
            "UNDER_REVIEW,        MORE_INFO_REQUIRED, Action needed: more information for claim CLM-TEST-7",
            "UNDER_REVIEW,        APPROVED,           Your claim CLM-TEST-7 has been approved",
            "UNDER_REVIEW,        REJECTED,           Decision on your claim CLM-TEST-7",
            "APPROVED,            SETTLED,            Your claim CLM-TEST-7 has been settled",
            "UNDER_REVIEW,        ON_HOLD,            Update on your claim CLM-TEST-7"})
    void eachStatusGetsItsOwnSubject(String from, String to, String subject) {
        assertEquals(subject, composer.compose(event(from, to)).subject());
    }

    @Test
    void theEmailGoesToTheClaimantAndCarriesTheEventId() {
        EmailMessage email = composer.compose(event("UNDER_REVIEW", "APPROVED"));

        assertEquals("jane.doe@example.com", email.toAddress());
        assertEquals("Jane Doe", email.toName());
        assertEquals("evt-1", email.eventId());
    }

    @Test
    void bothPartsGreetByNameAndLinkToTheClaimPage() {
        EmailMessage email = composer.compose(event("UNDER_REVIEW", "APPROVED"));

        assertTrue(email.text().startsWith("Hello Jane Doe,"));
        assertTrue(email.text().contains(TestEvents.PORTAL + "/claims/7"));
        assertTrue(email.html().contains("href=\"" + TestEvents.PORTAL + "/claims/7\""));
        assertTrue(email.html().contains("Hello Jane Doe,"));
    }

    @Test
    void aTrailingSlashOnThePortalAddressDoesNotDoubleUp() {
        NotificationProperties base = TestEvents.properties();
        NotificationProperties slash = new NotificationProperties(base.topic(), base.deadLetterTopic(), TestEvents.PORTAL + "/",
                base.dedupeCapacity(), base.mail(), base.retry());

        assertTrue(new EmailComposer(slash).compose(event(null, "SUBMITTED")).text().contains(TestEvents.PORTAL + "/claims/7"));
        assertFalse(new EmailComposer(slash).compose(event(null, "SUBMITTED")).text().contains("//claims"));
    }

    @Test
    void factsShowTheAmountWithItsMarketBecauseClaimsHaveNoCurrency() {
        String text = composer.compose(event("UNDER_REVIEW", "APPROVED")).text();

        assertTrue(text.contains("Claim number: CLM-TEST-7"));
        assertTrue(text.contains("Type: Property"));
        assertTrue(text.contains("Amount claimed: 1,250.00 (HK)"));
        assertTrue(text.contains("Status: Approved"));
        assertTrue(text.contains("Updated: 26 Oct 2026, 06:45 UTC"));
    }

    @Test
    void moreInformationIsLabelledAndAsksToAnswer() {
        EmailMessage email = composer.compose(event("UNDER_REVIEW", "MORE_INFO_REQUIRED"));

        assertTrue(email.text().contains("Status: More information required"));
        assertTrue(email.text().contains("Answer the question: " + TestEvents.PORTAL + "/claims/7"));
        assertTrue(email.html().contains(">Answer the question</a>"));
    }

    @Test
    void theOfficersNotesAreNeverIncludedOnlyAPointerToSignIn() {
        for (String to : new String[] {"APPROVED", "REJECTED"}) {
            String text = composer.compose(event("UNDER_REVIEW", to)).text();
            assertTrue(text.contains("Sign in to read"), to);
        }
    }

    @Test
    void anythingFromTheEventIsEscapedInTheHtml() {
        ClaimStatusChanged hostile = new ClaimStatusChanged("evt-2", 7L, "CLM-<b>1</b>", "PROPERTY", "HK", new BigDecimal("10"),
                null, "SUBMITTED", "x", Instant.parse("2026-10-26T06:45:17Z"), "jane@example.com", "<script>alert(1)</script> & \"Co\"");

        EmailMessage email = composer.compose(hostile);

        assertFalse(email.html().contains("<script>"));
        assertFalse(email.html().contains("<b>1</b>"));
        assertTrue(email.html().contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;Co&quot;"));
    }

    @Test
    void lineBreaksInValuesCannotBreakTheSubjectOrAddHeaders() {
        ClaimStatusChanged hostile = new ClaimStatusChanged("evt-2", 7L, "CLM-1\r\nBcc: victim@example.com", "MOTOR", "SG",
                new BigDecimal("10"), null, "SUBMITTED", "x", null, "jane@example.com", "Jane\r\nBcc: victim@example.com");

        EmailMessage email = composer.compose(hostile);

        assertFalse(email.subject().contains("\r"));
        assertFalse(email.subject().contains("\n"));
        assertFalse(email.toName().contains("\n"));
        assertFalse(email.text().lines().anyMatch(line -> line.startsWith("Bcc:")));
    }

    @Test
    void aMissingNameFallsBackToThere() {
        ClaimStatusChanged anonymous = new ClaimStatusChanged("evt-2", 7L, "CLM-1", "MOTOR", "SG", new BigDecimal("10"), null,
                "SUBMITTED", "x", null, "jane@example.com", "  ");

        assertTrue(composer.compose(anonymous).text().startsWith("Hello there,"));
    }

    @Test
    void optionalFactsAreLeftOutWhenTheEventLacksThem() {
        ClaimStatusChanged bare = new ClaimStatusChanged("evt-2", 7L, "CLM-1", null, null, null, null, "SUBMITTED", "x", null,
                "jane@example.com", "Jane");

        String text = composer.compose(bare).text();

        assertFalse(text.contains("Amount claimed"));
        assertFalse(text.contains("Updated:"));
        assertFalse(text.contains("Type:"));
        assertTrue(text.contains("Claim number: CLM-1"));
    }

    @Test
    void theAmountHasTwoDecimalsAndGroupsThousands() {
        assertEquals("2,400.75 (SG)", EmailComposer.amount(new BigDecimal("2400.75"), "sg"));
        assertEquals("500.00 (MY)", EmailComposer.amount(new BigDecimal("500"), "MY"));
        assertEquals("1,000,000.00", EmailComposer.amount(new BigDecimal("1000000"), null));
    }

    @Test
    void humanisesStatusCodes() {
        assertEquals("Under review", EmailComposer.humanise("UNDER_REVIEW"));
        assertEquals("More information required", EmailComposer.humanise("MORE_INFO_REQUIRED"));
        assertEquals("Settled", EmailComposer.humanise("SETTLED"));
        assertEquals("", EmailComposer.humanise(null));
    }
}
