package com.claims.notification.service;

import com.claims.notification.config.NotificationProperties;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The ids of events that already produced an email, so a duplicate (the backend's outbox delivers at least once) is skipped.
 * It remembers only the most recent {@code capacity} ids and only until the process restarts: enough to absorb a re-delivery
 * of recent events, not a guarantee across restarts (see ARCHITECTURE.md, part E).
 */
@Component
public class ProcessedEvents {
    private final Map<String, Boolean> seen;

    @Autowired
    public ProcessedEvents(NotificationProperties properties) {
        this(properties.dedupeCapacity());
    }

    ProcessedEvents(int capacity) {
        int limit = Math.max(1, capacity);
        this.seen = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > limit;
            }
        });
    }

    public boolean contains(String eventId) {
        return seen.containsKey(eventId);
    }

    public void add(String eventId) {
        seen.put(eventId, Boolean.TRUE);
    }
}
