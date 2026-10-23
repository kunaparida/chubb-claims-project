package com.claims.event;

/** Port for sending integration events to a message broker. Implementations must throw on failure. */
public interface EventPublisher {
    void publish(String topic, String key, String payload);
}
