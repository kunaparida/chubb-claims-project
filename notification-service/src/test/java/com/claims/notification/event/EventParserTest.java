package com.claims.notification.event;

import static org.junit.jupiter.api.Assertions.*;

import com.claims.notification.TestEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EventParserTest {
    private final EventParser parser = new EventParser(new ObjectMapper().findAndRegisterModules());

    @Test
    void readsTheJsonTheBackendPublishes() {
        ClaimStatusChanged event = parser.parse(
                TestEvents.json("evt-1", "CLM-1", "UNDER_REVIEW", "APPROVED", "jane@example.com", "Jane Doe"));

        assertEquals("evt-1", event.eventId());
        assertEquals(7L, event.claimId());
        assertEquals("CLM-1", event.claimNumber());
        assertEquals("PROPERTY", event.claimType());
        assertEquals("HK", event.market());
        assertEquals(0, new java.math.BigDecimal("1250.00").compareTo(event.amountClaimed()));
        assertEquals("UNDER_REVIEW", event.fromStatus());
        assertEquals("APPROVED", event.toStatus());
        assertEquals(Instant.parse("2026-10-26T06:45:17.123456Z"), event.occurredAt());
        assertEquals("jane@example.com", event.claimantEmail());
        assertEquals("Jane Doe", event.claimantName());
    }

    @Test
    void theFirstEventOfAClaimHasNoPreviousStatus() {
        ClaimStatusChanged event = parser.parse(TestEvents.json("evt-1", "CLM-1", null, "SUBMITTED", "jane@example.com", "Jane"));
        assertNull(event.fromStatus());
        assertEquals("SUBMITTED", event.toStatus());
    }

    @Test
    void fieldsAddedToTheEventLaterAreIgnored() {
        String json = TestEvents.json("evt-1", "CLM-1", null, "SUBMITTED", "jane@example.com", "Jane")
                .replace("{", "{\"somethingNew\":{\"a\":1},");
        assertEquals("evt-1", parser.parse(json).eventId());
    }

    @Test
    void aStatusTheBackendAddsLaterStillArrivesAsText() {
        assertEquals("ON_HOLD", parser.parse(TestEvents.json("evt-1", "CLM-1", "UNDER_REVIEW", "ON_HOLD", "jane@example.com", "Jane")).toStatus());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "null", "{}", "[]", "not json at all", "{\"eventId\":"})
    void refusesWhatIsNotAnEvent(String value) {
        assertThrows(BadEventException.class, () -> parser.parse(value));
    }

    @Test
    void refusesAnEventMissingWhatAnEmailNeeds() {
        assertThrows(BadEventException.class,
                () -> parser.parse(TestEvents.json(null, "CLM-1", null, "SUBMITTED", "jane@example.com", "Jane")));
        assertThrows(BadEventException.class,
                () -> parser.parse(TestEvents.json("evt-1", null, null, "SUBMITTED", "jane@example.com", "Jane")));
        assertThrows(BadEventException.class,
                () -> parser.parse(TestEvents.json("evt-1", "CLM-1", null, null, "jane@example.com", "Jane")));
        assertThrows(BadEventException.class,
                () -> parser.parse(TestEvents.json("evt-1", "CLM-1", null, "SUBMITTED", null, "Jane")));
        assertThrows(BadEventException.class, () -> parser.parse(
                TestEvents.json("evt-1", "CLM-1", null, "SUBMITTED", "jane@example.com", "Jane").replace("\"claimId\":7,", "")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "no-at-sign", "two@@example.com", "a b@example.com", "jane@example.com\\nBcc: victim@example.com",
            "@example.com", "jane@"})
    void refusesAnAddressThatCannotBeRight(String address) {
        assertThrows(BadEventException.class,
                () -> parser.parse(TestEvents.json("evt-1", "CLM-1", null, "SUBMITTED", address.replace("\\n", "\n"), "Jane")));
    }

    @Test
    void theErrorNeverQuotesThePayloadBecauseItHoldsAnEmailAddress() {
        String json = "{\"claimantEmail\":\"secret.person@example.com\", broken";
        BadEventException error = assertThrows(BadEventException.class, () -> parser.parse(json));
        assertFalse(String.valueOf(error.getMessage()).contains("secret.person"));
    }
}
