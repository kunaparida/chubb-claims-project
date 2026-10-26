<div align="center">

# Claims Management Platform

**APAC Motor & Property Claims · Explicit Workflows · Traceable Decisions**

A full-stack claims management application built with **Java, Spring Boot, and Angular** for claim submission, assessment, decision tracking, and management reporting.

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-22-DD0031?logo=angular&logoColor=white)](https://angular.dev/)
[![Database](https://img.shields.io/badge/Database-H2%20in--memory-blue)](https://www.h2database.com/)
[![Events](https://img.shields.io/badge/Events-Kafka-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Containers](https://img.shields.io/badge/Containers-Docker%20Compose-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

[Quick Start](#quick-start--run-it-locally) · [Build & Test](#build-and-test) · [API Guide](#api-overview) · [Architecture](ARCHITECTURE.md) · [AI Journal](AI_JOURNAL.md) · [Docker Guide](DOCKER.md)

</div>

---

## What this solves

A small full-stack claims platform that replaces scattered claim tracking with **role-specific workflows, controlled status transitions, and an auditable history**. The backend is authoritative for business rules and access control; the Angular frontend provides the user experience for each role.

| Role | What they can do |
|---|---|
| **Claimant** | Register, report incidents (and optionally attach photos or documents), track claims and history, answer information requests, read decisions, and **get an email each time the claim changes status** |
| **Claims officer** | Work the oldest-first queue, pick up or review claims, read the claimant's documents, request information, approve or reject claims, and mark approved claims as settled |
| **Manager** | View all claims, assign claims to officers, and review workload, liability by market/type, and officer performance |

## Technology stack

| Area | Technologies |
|---|---|
| Backend | Java 21, Spring Boot 3.4, Spring MVC, Spring Security with JWT, Spring Data JPA, Bean Validation |
| Frontend | Angular 22, standalone components, signals, zoneless change detection |
| Persistence | In-memory H2 (the only database in use; data resets on restart). PostgreSQL is only a commented datasource example: no driver is included |
| Events | Transactional outbox with log publisher by default and optional Kafka publisher |
| Notifications | A separate **notification service** (Spring Boot, Spring Kafka, Spring Mail) that reads the Kafka events and emails the claimant; Mailpit is the local inbox in Docker |
| API documentation | OpenAPI / Swagger UI: every endpoint has a summary and documented error responses, and the request bodies carry examples |
| Packaging | Docker Compose configuration (execution still needs environment validation) |

> **Verification note (2026-10-26):** the backend has 118 passing tests (JaCoCo 97.1% of lines), the notification service 76 (99.0% of lines), and the frontend 312, plus a clean production build. The real backend, notification service, a Kafka broker and an SMTP server were also run together outside Docker, and one claim produced six emails in order. **Docker Compose has not been run**, and no real mail provider has been tried.

## Architecture at a glance

The frontend calls the Spring Boot REST API through the Angular development proxy. The backend enforces authentication, authorization, validation, and claim workflow rules. Claim updates, audit history, and outbox events are saved in one database transaction; a scheduled relay publishes events to the configured publisher. When Kafka is switched on, the notification service reads each event and emails the claimant. More diagrams (system picture, outbox flow, claim lifecycle) are in [ARCHITECTURE.md](ARCHITECTURE.md).

```mermaid
flowchart TB
    U[Claimant / Claims Officer / Manager] --> UI[Angular 22 Frontend]
    UI -->|/api via proxy| API[Spring Boot REST API]
    API --> SEC[JWT Authentication & Role-Based Access]
    SEC --> CTRL[Controllers]
    CTRL --> SVC[Services<br/>Validation & Claim Workflow]
    SVC --> REPO[Spring Data JPA Repositories]
    REPO --> DB[(H2 In-Memory Database)]
    SVC -->|same transaction| AUDIT[Claim Status History]
    SVC -->|same transaction| OUTBOX[Transactional Outbox]
    OUTBOX --> RELAY[Scheduled Outbox Relay]
    RELAY --> PUB{Configured Publisher}
    PUB --> LOG[Application Log]
    PUB --> KAFKA[Kafka<br/>Optional]
    KAFKA --> NOTIFY[Notification Service<br/>Kafka consumer]
    NOTIFY --> MAIL[SMTP server or Mailpit inbox<br/>Email to the claimant]
    NOTIFY -->|after retries fail| DLT[Dead-letter topic]
```

> **Current implementation:** H2 is the active database and the log publisher is the default, so the app runs with no Kafka. With Kafka switched on, the notification service sends the emails. Kafka was run against a real broker outside Docker; Docker Compose execution remains pending validation.

## Claim lifecycle

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED: claim reported
    SUBMITTED --> UNDER_REVIEW: start review
    UNDER_REVIEW --> MORE_INFO_REQUIRED: request information
    MORE_INFO_REQUIRED --> UNDER_REVIEW: claimant answers or staff resume
    UNDER_REVIEW --> APPROVED: approve
    UNDER_REVIEW --> REJECTED: reject
    APPROVED --> SETTLED: settle
    REJECTED --> [*]
    SETTLED --> [*]
```

The service layer rejects invalid transitions with **HTTP 409 Conflict**. Picking up or assigning a claim does not change its status; it remains `SUBMITTED` until review begins.

## Repository layout

```
backend/        Spring Boot service      src/, pom.xml, postman/, CLAUDE.md
frontend/       Angular app              src/, angular.json, proxy.conf.json, CLAUDE.md
notification-service/  Kafka consumer    reads the events and emails the claimant (src/, pom.xml, Dockerfile, CLAUDE.md)
docs/           SWAGGER-TEST-GUIDE.txt   (API walkthrough in Swagger)
                E2E-VALIDATION-FLOW.txt  (manual test script for the whole system)
                diagrams/, screenshots/  (the pictures used in ARCHITECTURE.md and this README)
docker-compose.yml, DOCKER.md            run everything (Kafka, notification service, Mailpit) in containers; the guide to do it
ARCHITECTURE.md                          one architecture document: backend, frontend, how they fit
AI_JOURNAL.md                            one AI working journal: what was asked, accepted, challenged, overridden
CLAUDE.md                                guidance for AI assistants working in this repository
```

## Quick start — run it locally

**You need:** JDK 21 or newer, Maven 3.6.3+ (what Spring Boot 3.4 requires), and Node 22.22.3+, 24.15+ or 26+ (the range Angular 22 requires; the frontend was built and
tested with Node 26 and npm 11).

**1. Start the backend** (first, always):

```bash
cd backend
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. The database is **in memory and resets on every restart** (accounts and
claims are lost; the two staff accounts below are re-created).

**2. Start the frontend** (second terminal):

```bash
cd frontend
npm install
npm start
```

**3. Open <http://localhost:4200>** and sign in.

| Account | Email | Password |
|---|---|---|
| Claims officer (local demo only) | `officer@claims.local` | `ChangeMe123!` |
| Manager (local demo only) | `manager@claims.local` | `ChangeMe123!` |
| Claimant | register yourself with **Create an account** | your choice (8–72 characters) |

These seeded credentials are for local assessment/demo use only; change or disable them before any shared environment. The login page has a "Local demo accounts" panel whose buttons fill in the officer or manager email and password (you still press "Sign in"). Override the seeded password with the `BOOTSTRAP_PASSWORD`
environment variable, or switch the seeded accounts off with `BOOTSTRAP_USERS=false`.

| URL | What |
|---|---|
| `http://localhost:4200` | the application |
| `http://localhost:8080/swagger-ui/index.html` | API documentation (OpenAPI JSON at `/v3/api-docs`) |
| `http://localhost:8080/h2-console` | database console, **off by default**. Start the backend with `H2_CONSOLE_ENABLED=true` to use it: JDBC URL `jdbc:h2:mem:claimsdb`, user `sa`, empty password. It needs no sign-in, so never enable it anywhere shared |

> **Why the frontend talks to the backend through a proxy.** The backend has no CORS configuration, so a browser on port 4200
> cannot call port 8080 directly (the browser blocks the cross-origin call). `npm start` proxies `/api` to the backend
> (`frontend/proxy.conf.json`), so always use `http://localhost:4200`. See [ARCHITECTURE.md](ARCHITECTURE.md), section 10.2.

## Run with Docker Compose (backend + notification service + frontend + Kafka)

> **Validation status:** Docker and Kafka configuration is documented, but the available development record does not contain a successful `docker compose up` run. Treat the container workflow as pending validation until it has been built and tested in your environment.

With Docker Desktop installed and ports 4200, 8025, 8080 and 9094 free, the following command is intended to build and start the whole stack: Kafka for the claim events, the notification service, and Mailpit, a local inbox that catches its emails:

```bash
docker compose up --build -d
```

Then open <http://localhost:4200> for the app and <http://localhost:8025> for the emails (Mailpit; nothing is sent to a real address). **[DOCKER.md](DOCKER.md)** has the full steps: installing WSL 2 and Docker Desktop, checking the install,
watching events arrive in Kafka, reading the emails, every day-to-day command, a Kafka configuration check, and troubleshooting. The stack is intended to run the backend (in-memory H2 database), the notification service, Mailpit, the frontend behind nginx, and a single-node Kafka broker. The H2 console is off unless you start the stack with `H2_CONSOLE_ENABLED=true`; it needs no sign-in and must not be exposed in a shared or production environment. See `DOCKER.md` for setup and verification steps.

## Email notifications

Every time a claim changes status, the claimant gets an email: claim received, under review, more information needed, back under review, approved, rejected, settled. The `notification-service/` application does this. It is separate from the backend, so a slow or broken mail server can never hold up a claim update.

How it works, in short:

1. The backend saves the status change and an event together, then publishes the event to the Kafka topic `claims.claim-status-changed`.
2. The notification service reads the event and sends one email to the claimant (never to the officer). The email has a text part and an HTML part, and a link to the claim page.
3. If the mail server fails, the service tries again after 1, 2 and 4 seconds. If it still fails, the event is copied to a dead-letter topic (`claims.claim-status-changed.dlt`) so it is not lost.
4. If the same event arrives twice, the second one is skipped. After a restart the service carries on where it stopped.

**To see it:** use Docker Compose (above), change a claim's status in the app, then open <http://localhost:8025>. Emails only happen when the backend publishes to Kafka (`EVENTS_PUBLISHER=kafka`, which Compose sets).

**To run it by hand** you need a Kafka broker (for example `docker compose up -d kafka kafka-init`):

```bash
# the backend must publish to Kafka
cd backend               && EVENTS_PUBLISHER=kafka SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9094 mvn spring-boot:run
# the notification service; by default it only prints each email in its log, so you need no mail server
cd notification-service  && SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9094 mvn spring-boot:run
```

(On Windows PowerShell set the variables first with `$env:NAME = "value"`.) To really send email, set `NOTIFICATIONS_MAIL_MODE=smtp` and `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH=true`, `MAIL_STARTTLS=true`, `MAIL_FROM` in the environment. Never put a password in a file. All the settings are listed in [ARCHITECTURE.md](ARCHITECTURE.md), section 7.4.

The claimant's email address travels inside the event, so limit who can read the topics. Without Kafka or Docker you can still see the service work: `cd notification-service && mvn test` starts a real Kafka broker and a real SMTP server inside the tests.

## Validate the application end to end

* **`docs/E2E-VALIDATION-FLOW.txt`** — a tick-box script with fixed test data (two claimants, three claims) that exercises every
  role and checks the dashboard numbers at six checkpoints (A-F) and a final state. It takes 25-30 minutes. Restart the backend first so you start clean.
  Its optional Part 13 checks the emails (it needs the Docker stack).
* **Swagger** — `docs/SWAGGER-TEST-GUIDE.txt` explains how to authorise and call each endpoint.
* **Postman** — `backend/postman/Claims-Management.postman_collection.json`: run the requests in collection order; the login
  requests capture the tokens and ids into collection variables.

## API overview

All endpoints except register and login need `Authorization: Bearer <token>`. Errors are RFC 7807 `application/problem+json`.

| Endpoint | Access | Purpose |
|---|---|---|
| `POST /api/auth/register` | public | Create a claimant account; returns a JWT |
| `POST /api/auth/login` | public | Sign in (any role); returns a JWT |
| `GET /api/auth/me` | any | The signed-in user's profile |
| `POST /api/claims` | CLAIMANT | Submit a claim; `201` with a `Location` header pointing at the new claim |
| `GET /api/claims?status=&page=&size=` | any | Own claims (claimant), assigned claims (officer), all claims (manager) |
| `GET /api/claims/queue` | OFFICER, MANAGER | Unassigned `SUBMITTED` claims, oldest first |
| `GET /api/claims/{id}` | any | One claim (claimants only their own; officers their own or unassigned) |
| `GET /api/claims/{id}/history` | same as view | Audit trail of every status change |
| `POST /api/claims/{id}/documents` | CLAIMANT (owner), claim still open | Attach one optional file (PDF, JPG or PNG) as `multipart/form-data` field `file`; `201` |
| `GET /api/claims/{id}/documents` | same as view | List a claim's documents (name, type, size, who, when) |
| `GET /api/claims/{id}/documents/{documentId}` | same as view | Download one document, sent as an attachment |
| `PUT /api/claims/{id}/pickup` | OFFICER | Take an unassigned claim from the queue |
| `PUT /api/claims/{id}/assign` | OFFICER (self), MANAGER (anyone) | Assign an officer — `{"officerId": 2}` |
| `PUT /api/claims/{id}/review` | assigned OFFICER, MANAGER | Begin or resume review |
| `PUT /api/claims/{id}/request-info` | assigned OFFICER, MANAGER | Ask the claimant a question — `{"note": "..."}` |
| `PUT /api/claims/{id}/provide-info` | CLAIMANT (owner) | Answer it — `{"response": "..."}` |
| `PUT /api/claims/{id}/approve` · `/reject` · `/settle` | assigned OFFICER, MANAGER | Decide or settle — `{"note": "..."}` |
| `GET /api/dashboard/workload` · `/liability` · `/performance` | MANAGER | Counts by status, exposure by market and type, per-officer load |

**Workflow.** `SUBMITTED → UNDER_REVIEW`; `UNDER_REVIEW → MORE_INFO_REQUIRED | APPROVED | REJECTED`;
`MORE_INFO_REQUIRED → UNDER_REVIEW`; `APPROVED → SETTLED`. `REJECTED` and `SETTLED` are final. Anything else is `409`. Picking up or
assigning a claim only sets the officer — the status stays `SUBMITTED` until the officer starts the review.

**Submitting a claim** (`claimantEmail` must be the signed-in account's email; description at least 20 characters; amount greater
than zero with at most two decimals; incident date not in the future; `market` a 2-letter code):

The **report form in the UI** adds three rules of its own that the API does not enforce: the market is picked from six
(Singapore SG, Hong Kong HK, India IN, Malaysia MY, Thailand TH, Indonesia ID), only incidents from the last 7 days can be chosen, and the
policy number is generated (`POL-<year>-<six digits>`). The API still accepts any two-letter market, any past date and any
policy number up to 64 characters. If a required field is missing or wrong, the form shows a message next to the field and a list of everything to fix.

```json
{
  "policyNumber": "POL-2026-00042",
  "claimantName": "Sample Claimant",
  "claimantEmail": "claimant@example.com",
  "incidentDate": "2026-10-10",
  "description": "Rear bumper damage from a parking collision.",
  "amountClaimed": 1250.00,
  "claimType": "MOTOR",
  "market": "SG"
}
```

**Supporting documents** are optional and never stop a claim from being submitted. The claimant who owns a claim can attach up to **5 files of 5 MB each**, as PDF, JPG or PNG, while the claim is open (not after it is rejected or settled). Anyone who can see the claim can download them; officers and managers cannot add any. The backend checks what is **inside** the file, not its name, so a renamed text file is refused. Files are kept in the in-memory database, so they are lost on restart. From the command line:

```bash
curl -H "Authorization: Bearer $TOKEN" -F "file=@estimate.pdf" http://localhost:8080/api/claims/1/documents
```

## Event publishing — transactional outbox

Every status change writes an audit row **and** an outbox row in the same database transaction. A scheduled relay publishes
unpublished rows as `ClaimStatusChanged` events and marks them published (at-least-once; each event has an `eventId` for
de-duplication; the claim id is the Kafka key so one claim's events stay in order).

| Setting | Default | Meaning |
|---|---|---|
| `app.events.publisher` (`EVENTS_PUBLISHER`) | `log` | `log` writes the event to the application log (no broker needed); `kafka` publishes to Kafka |
| `spring.kafka.bootstrap-servers` | not set (commented out) | Required when the publisher is `kafka`; supply it as the property or the environment variable `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| `app.events.topic` (`EVENTS_TOPIC`) | `claims.claim-status-changed` | Target topic |
| `app.outbox.poll-ms` (`OUTBOX_POLL_MS`) | `5000` | Relay interval |

Each event also carries the claimant's `claimantEmail` and `claimantName`, so the notification service knows who to write to. Those are personal data, so limit who can read the topic. The officer's notes are never in the event.

The Kafka publisher is unit-tested with a mock, and it was also run against a real Kafka broker (started inside a JVM, not the Docker stack) on 2026-10-26. The Docker stack provides a broker too, and
`DOCKER.md` part 5 is how to prove that setup.

## Build and test

```bash
cd backend  && mvn test                         # 118 unit + Spring Boot integration tests (H2) and the JaCoCo coverage report
cd notification-service && mvn test             # 76 tests; a real Kafka broker and SMTP server start inside the tests (1-2 minutes)
cd frontend && npm test -- --no-watch           # Vitest: 312 unit/component tests
cd frontend && npm run build                    # strict production build
```

Backend tests cover the service rules, the audit recorder, the outbox relay and publishers, validation, the API contract (the `Location`
header, the H2 console being closed by default, every endpoint documented), the document rules, the fields the event must keep, and the end-to-end Spring Boot flows (118 tests).
`mvn test` also writes the coverage report to `backend/target/site/jacoco/index.html`; measured on 2026-10-26 (JDK 21, JaCoCo 0.8.15):
**97.1% of lines, 86.9% of branches**. The notification service's tests cover the wording of every email, bad records, duplicates, retries and the dead-letter topic
(**99.0% of lines, 88.6% of branches**). Frontend tests cover the API contract (every verb, path and body),
the interceptor and guards, an exhaustive check of the UI's "who can do what" table against the backend workflow, the report form's rules and messages, and each screen.

## Project documentation

| File | What is in it |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | System overview with diagrams; backend design and security; events and the notification service; frontend; integration decisions (contract, proxy, money); testing; limitations and next steps |
| [AI_JOURNAL.md](AI_JOURNAL.md) | How AI was used: the prompts that shaped the design or found defects, what was accepted, challenged or overridden, and what was and was not verified |
| [backend/CLAUDE.md](backend/CLAUDE.md), [frontend/CLAUDE.md](frontend/CLAUDE.md), [notification-service/CLAUDE.md](notification-service/CLAUDE.md), [CLAUDE.md](CLAUDE.md) | Working rules for AI assistants, per area |
| [DOCKER.md](DOCKER.md) | Install Docker, run the whole system (Kafka, notification service, Mailpit) in containers, verify events and emails, troubleshoot |
| [docs/E2E-VALIDATION-FLOW.txt](docs/E2E-VALIDATION-FLOW.txt) | Manual end-to-end test script |
| [docs/SWAGGER-TEST-GUIDE.txt](docs/SWAGGER-TEST-GUIDE.txt) | Testing the API through Swagger |

## Known limitations and deployment considerations

The current implementation is a development/assessment baseline, not a production deployment configuration. Review [ARCHITECTURE.md](ARCHITECTURE.md), section 12, before deploying. Key considerations are: in-memory H2 data (including uploaded documents, which are not virus-scanned) is lost on restart; the H2 console is opt-in (off by default) and unauthenticated when enabled; the backend has no CORS configuration and relies on a proxy or same-origin gateway; claim amounts have no currency; optimistic locking is not implemented; claimants' email addresses travel on the Kafka topic, and the notification service remembers handled events only in memory; and Docker execution (including the notification service and Mailpit containers) has not been confirmed in the recorded verification evidence.
