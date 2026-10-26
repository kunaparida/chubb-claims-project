package com.claims.notification;

import com.claims.notification.config.NotificationProperties;
import com.claims.notification.event.ClaimStatusChanged;
import java.math.BigDecimal;
import java.time.Instant;

/** Fixtures. {@link #json} has the exact shape the claims backend writes (see its {@code ClaimStatusChangedEvent}). */
public final class TestEvents {
    public static final String PORTAL = "http://portal.test";

    private TestEvents() {}

    public static NotificationProperties properties() {
        return new NotificationProperties("claims.claim-status-changed", "claims.claim-status-changed.dlt", PORTAL, 1000,
                new NotificationProperties.Mail("log", "Claims Platform <no-reply@claims.local>"),
                new NotificationProperties.Retry(3, 10, 2.0, 100));
    }

    public static ClaimStatusChanged event(String eventId, String from, String to) {
        return new ClaimStatusChanged(eventId, 7L, "CLM-TEST-7", "PROPERTY", "HK", new BigDecimal("1250.00"), from, to,
                "officer@claims.local", Instant.parse("2026-10-26T06:45:17Z"), "jane.doe@example.com", "Jane Doe");
    }

    /** What the backend publishes: field names and value shapes as its Jackson mapper writes them. */
    public static String json(String eventId, String claimNumber, String from, String to, String email, String name) {
        return "{\"eventId\":" + quote(eventId) + ",\"claimId\":7,\"claimNumber\":" + quote(claimNumber)
                + ",\"claimType\":\"PROPERTY\",\"market\":\"HK\",\"amountClaimed\":1250.00,\"fromStatus\":" + quote(from)
                + ",\"toStatus\":" + quote(to) + ",\"changedBy\":\"officer@claims.local\",\"occurredAt\":\"2026-10-26T06:45:17.123456Z\""
                + ",\"claimantEmail\":" + quote(email) + ",\"claimantName\":" + quote(name) + "}";
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
