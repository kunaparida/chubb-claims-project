# CLAUDE.md — Notification Service

Guidance for Claude Code (and any human) working in `notification-service/`. The repository-wide rules are in [../CLAUDE.md](../CLAUDE.md).

## What this is

A small Spring Boot 3.4.5 / Java 21 application (port 8081, **no database, no business API**). It reads `ClaimStatusChanged` events from the Kafka
topic the claims backend publishes to (through its transactional outbox) and sends the **claimant** an email for each one. Design and guarantees:
[../ARCHITECTURE.md](../ARCHITECTURE.md) B.10.

```
com.claims.notification
  NotificationServiceApplication
  consumer/ClaimStatusConsumer        the @KafkaListener; parse, then hand to the service
  event/ClaimStatusChanged            OUR reading of the backend's event JSON (statuses stay text; unknown fields ignored)
        EventParser, BadEventException   validate; a record that can never work is a BadEventException (not retried)
  service/NotificationService         one event in, at most one email out; ProcessedEvents = in-memory duplicate protection
  mail/EmailComposer                  the wording per status, text + HTML; EmailSender (SmtpEmailSender | LogEmailSender); Masking
  config/NotificationProperties       every setting (see application.properties for the environment variable of each)
         MailConfig                   picks smtp or log, fails at start-up on a wrong mode
         KafkaErrorHandlingConfig     retries with a growing wait, then the dead-letter topic
```

## Tooling and commands

* Maven is not on PATH on the author's machine; they use IntelliJ's bundled Maven with JDK 21. Offline (`mvn -o`) works once dependencies are cached.
* `mvn test` runs **76 tests in about one to two minutes**: unit tests, the SMTP sender against GreenMail, and two Spring Boot tests that start a **real Kafka broker
  inside the JVM** (`@EmbeddedKafka`, KRaft, 3 partitions) with a real SMTP server. No Docker needed.
* `mvn package -DskipTests` builds `target/claims-notification-service-1.0.0.jar`.
* To run by hand you need a Kafka broker you provide (the compose file has one): `SPRING_KAFKA_BOOTSTRAP_SERVERS=...`. `NOTIFICATIONS_MAIL_MODE=log`
  prints emails instead of sending them. Defaults suit Mailpit on `localhost:1025`.

## Rules

1. **The event is a contract owned by the backend** (`backend/.../ClaimStatusChangedEvent`). Read what you need, ignore the rest, and keep statuses as
   text so a status the backend adds later still produces an email. If the contract must change, change the backend in a separate step and record it
   in `AI_JOURNAL.md`; never edit `backend/` from here.
2. **Personal data.** The event holds the claimant's email address and name. Log addresses only through `Masking.email`; never put a payload or a Jackson
   message into a log line or an exception text (Jackson quotes the start of the input); `LogEmailSender` (mode `log`) prints whole emails and is for local use only.
   Passwords come only from the environment (`MAIL_PASSWORD`), never from a file.
3. **Order matters in `NotificationService`:** send first, remember the event id afterwards. Marking first would lose an email when sending fails.
4. **Failure handling is the error handler's job, not the listener's.** The listener throws; `KafkaErrorHandlingConfig` decides: `EmailDeliveryException` is
   retried, `BadEventException` is dead-lettered at once. Do not catch and swallow exceptions in the listener or the senders.
5. **`@KafkaListener` must keep `idIsGroup = false`** with `groupId = ${spring.kafka.consumer.group-id}`. Without it Spring Kafka uses the listener id as the
   group and silently ignores `NOTIFICATIONS_GROUP_ID` (found in a live run; `NotificationFlowTest` pins it).
6. **No new channels or templates without a decision:** email only, one wording per status, English. Each is a listed limitation (ARCHITECTURE part E).
7. **Tests with a Kafka broker use the existing base class** (`KafkaFlowTestBase`): topics `it.claim-status-changed` and `.dlt`, short retries, a dead-letter
   reader. Prefer a unit test when the behaviour does not need a broker.
8. Format and conventions as in the other applications: constructor injection, small classes, comments that say *why*. **Commit only when asked.** Ask before
   installing or downloading anything. Update `AI_JOURNAL.md` in the same change as any non-trivial AI-driven step, including what was corrected.

## Not verified (as of 2026-10-26)

The Docker image and the compose entries (`notification-service`, `mailpit`) have never been built or run; a real SMTP provider (TLS, authentication) was
never used. The real applications were run together once, outside Docker (backend jar, this service, a Kafka broker and an SMTP server in a JVM).
