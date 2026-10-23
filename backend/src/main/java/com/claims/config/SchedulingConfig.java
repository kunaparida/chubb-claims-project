package com.claims.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the outbox poller. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
