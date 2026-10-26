package com.claims.notification.config;

import static org.junit.jupiter.api.Assertions.*;

import com.claims.notification.event.BadEventException;
import com.claims.notification.mail.EmailDeliveryException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

class KafkaErrorHandlingConfigTest {

    @Test
    void aMailFailureIsDescribedByItsRootCause() {
        Exception failure = new ListenerExecutionFailedException("listener failed",
                new EmailDeliveryException("could not send", new IllegalStateException("connection refused")));

        assertEquals("IllegalStateException: connection refused", KafkaErrorHandlingConfig.describe(failure));
    }

    @Test
    void theLogStopsAtOurOwnMessageBecauseTheJsonErrorUnderneathCanQuoteThePayload() {
        Exception jsonError = assertThrows(JsonParseException.class,
                () -> new ObjectMapper().readValue("secret.person@example.com {", Object.class));
        assertTrue(jsonError.getMessage().contains("secret"), "the premise: Jackson quotes the start of the payload");

        String described = KafkaErrorHandlingConfig.describe(new ListenerExecutionFailedException("listener failed",
                new BadEventException("The record is not a valid ClaimStatusChanged event", jsonError)));

        assertEquals("BadEventException: The record is not a valid ClaimStatusChanged event", described);
        assertFalse(described.contains("secret"));
    }
}
