package com.claims.notification.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Reads a record's JSON and checks it has what an email needs. Anything else is a {@link BadEventException}. */
@Component
public class EventParser {
    /** Deliberately simple: one @, no spaces or control characters (which also rules out header injection). Strict parsing happens at send time. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    private final ObjectMapper objectMapper;

    public EventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ClaimStatusChanged parse(String json) {
        if (json == null || json.isBlank()) {
            throw new BadEventException("The record has no value");
        }
        ClaimStatusChanged event;
        try {
            event = objectMapper.readValue(json, ClaimStatusChanged.class);
        } catch (JsonProcessingException ex) {
            // The message of a Jackson error can quote the payload, which holds an email address: keep it out of the exception text.
            throw new BadEventException("The record is not a valid ClaimStatusChanged event", ex);
        }
        if (event == null) {
            throw new BadEventException("The record is empty");
        }
        require(event.eventId(), "eventId");
        require(event.claimNumber(), "claimNumber");
        require(event.toStatus(), "toStatus");
        if (event.claimId() == null) {
            throw new BadEventException("The event has no claimId");
        }
        if (event.claimantEmail() == null || !EMAIL.matcher(event.claimantEmail().trim()).matches()) {
            throw new BadEventException("The event has no usable claimantEmail (event " + event.eventId() + ")");
        }
        return event;
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadEventException("The event has no " + field);
        }
    }
}
