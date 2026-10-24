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
| **Claimant** | Register, submit incidents, track claims and history, respond to information requests, and read decisions |
| **Claims officer** | Work the oldest-first queue, pick up or review claims, request information, approve or reject claims, and mark approved claims as settled |
| **Manager** | View all claims, assign claims to officers, and review workload, liability by market/type, and officer performance |

## Technology stack

| Area | Technologies |
|---|---|
| Backend | Java 21, Spring Boot 3.4, Spring MVC, Spring Security with JWT, Spring Data JPA, Bean Validation |
| Frontend | Angular 22, standalone components, signals, zoneless change detection |
| Persistence | In-memory H2 (the only database in use; data resets on restart). PostgreSQL is only a commented datasource example: no driver is included |
| Events | Transactional outbox with log publisher by default and optional Kafka publisher |
| API documentation | OpenAPI / Swagger UI: every endpoint has a summary and documented error responses, and the request bodies carry examples |
| Packaging | Docker Compose configuration (execution still needs environment validation) |

> **Verification note:** Backend tests and coverage were re-run on 2026-10-24 (85 tests passing; JaCoCo 97.0% line coverage, JDK 21). The 216 frontend tests were last re-run on 2026-10-23. Docker Compose and publishing to a real Kafka broker remain unverified in the available evidence.

## Repository layout

```
backend/        Spring Boot service      src/, pom.xml, postman/, CLAUDE.md
frontend/       Angular app              src/, angular.json, proxy.conf.json, CLAUDE.md
docs/           SWAGGER-TEST-GUIDE.txt   (API walkthrough in Swagger)
                E2E-VALIDATION-FLOW.txt  (25-30 minute manual test script for the whole system)
docker-compose.yml, DOCKER.md            run everything (with Kafka) in containers; the guide to do it
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

These seeded credentials are for local assessment/demo use only; change or disable them before any shared environment. The login page has a "Local demo accounts" panel whose buttons fill in the officer or manager email. Override the seeded password with the `BOOTSTRAP_PASSWORD`
environment variable, or switch the seeded accounts off with `BOOTSTRAP_USERS=false`.

| URL | What |
|---|---|
| `http://localhost:4200` | the application |
| `http://localhost:8080/swagger-ui/index.html` | API documentation (OpenAPI JSON at `/v3/api-docs`) |
| `http://localhost:8080/h2-console` | database console, **off by default**. Start the backend with `H2_CONSOLE_ENABLED=true` to use it: JDBC URL `jdbc:h2:mem:claimsdb`, user `sa`, empty password. It needs no sign-in, so never enable it anywhere shared |

> **Why the frontend talks to the backend through a proxy.** The backend has no CORS configuration, so a browser on port 4200
> cannot call port 8080 directly (the browser blocks the cross-origin call). `npm start` proxies `/api` to the backend
> (`frontend/proxy.conf.json`), so always use `http://localhost:4200`. See [ARCHITECTURE.md](ARCHITECTURE.md), part D.

## Run with Docker Compose (backend + frontend + Kafka)

> **Validation status:** Docker and Kafka configuration is documented, but the available development record does not contain a successful `docker compose up` run. Treat the container workflow as pending validation until it has been built and tested in your environment.

With Docker Desktop installed and ports 4200, 8080 and 9094 free, the following command is intended to build and start the application stack, including Kafka for claim events:

```bash
docker compose up --build -d
```

Then open <http://localhost:4200>. **[DOCKER.md](DOCKER.md)** has the full steps: installing WSL 2 and Docker Desktop, checking the install,
watching events arrive in Kafka, every day-to-day command, a Kafka configuration check, and troubleshooting. The stack is intended to run the backend (in-memory H2 database), the frontend behind nginx, and a single-node Kafka broker. The H2 console is off unless you start the stack with `H2_CONSOLE_ENABLED=true`; it needs no sign-in and must not be exposed in a shared or production environment. See `DOCKER.md` for setup and verification steps.

## Validate the application end to end

* **`docs/E2E-VALIDATION-FLOW.txt`** — a tick-box script with fixed test data (two claimants, three claims) that exercises every
  role and checks the dashboard numbers at six checkpoints (A-F) and a final state. It takes 25-30 minutes. Restart the backend first so you start clean.
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

The Kafka publisher is wired and unit-tested but has **not** been run against a real broker; the Docker stack above provides one, and
`DOCKER.md` part 5 is how to prove it.

## Build and test

```bash
cd backend  && mvn test                         # 85 unit + Spring Boot integration tests (H2) and the JaCoCo coverage report
cd frontend && npm test -- --no-watch           # Vitest: 216 unit/component tests
cd frontend && npm run build                    # strict production build
```

Backend tests cover the service rules, the audit recorder, the outbox relay and publishers, validation, the API contract (the `Location`
header, the H2 console being closed by default, every endpoint documented) and the end-to-end Spring Boot flows (85 tests).
`mvn test` also writes the coverage report to `backend/target/site/jacoco/index.html`; measured on 2026-10-24 (JDK 21, JaCoCo 0.8.15):
**97.0% of lines, 85.7% of branches**. Frontend tests cover the API contract (every verb, path and body),
the interceptor and guards, an exhaustive check of the UI's "who can do what" table against the backend workflow, and each screen.

## Project documentation

| File | What is in it |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | System overview; backend design and security; frontend structure; integration decisions (contract, proxy, auth, money); limitations and next steps |
| [AI_JOURNAL.md](AI_JOURNAL.md) | How AI was used: the prompts that shaped the design or found defects, what was accepted, challenged or overridden, and what was and was not verified |
| [backend/CLAUDE.md](backend/CLAUDE.md), [frontend/CLAUDE.md](frontend/CLAUDE.md), [CLAUDE.md](CLAUDE.md) | Working rules for AI assistants, per area |
| [DOCKER.md](DOCKER.md) | Install Docker, run the whole system with Kafka in containers, verify events, troubleshoot |
| [docs/E2E-VALIDATION-FLOW.txt](docs/E2E-VALIDATION-FLOW.txt) | Manual end-to-end test script |
| [docs/SWAGGER-TEST-GUIDE.txt](docs/SWAGGER-TEST-GUIDE.txt) | Testing the API through Swagger |

## Known limitations and deployment considerations

The current implementation is a development/assessment baseline, not a production deployment configuration. Review [ARCHITECTURE.md](ARCHITECTURE.md), Part E, before deploying. Key considerations are: in-memory H2 data is lost on restart; the H2 console is opt-in (off by default) and unauthenticated when enabled; the backend has no CORS configuration and relies on a proxy or same-origin gateway; claim amounts have no currency; optimistic locking is not implemented; and Docker/Kafka execution has not been confirmed in the recorded verification evidence.
