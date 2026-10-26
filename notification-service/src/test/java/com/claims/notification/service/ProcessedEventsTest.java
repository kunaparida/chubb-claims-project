package com.claims.notification.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProcessedEventsTest {

    @Test
    void remembersWhatWasAdded() {
        ProcessedEvents events = new ProcessedEvents(10);

        assertFalse(events.contains("a"));
        events.add("a");
        assertTrue(events.contains("a"));
        assertFalse(events.contains("b"));
    }

    @Test
    void forgetsTheOldestOnceFull() {
        ProcessedEvents events = new ProcessedEvents(2);

        events.add("a");
        events.add("b");
        events.add("c");

        assertFalse(events.contains("a"), "the oldest id is dropped");
        assertTrue(events.contains("b"));
        assertTrue(events.contains("c"));
    }

    @Test
    void aCapacityBelowOneStillKeepsTheLatest() {
        ProcessedEvents events = new ProcessedEvents(0);

        events.add("a");
        events.add("b");

        assertTrue(events.contains("b"));
        assertFalse(events.contains("a"));
    }
}
