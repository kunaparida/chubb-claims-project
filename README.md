# Claims Platform

APAC motor and property claims: claimants report and track incidents, claims officers work them to a decision and settlement,
and managers see workload, liability and officer performance. **One repository, two applications:**

| Folder | What it is |
|---|---|
| [`backend/`](backend) | REST API — Java 21, Spring Boot 3.4, Spring Security (JWT), Spring Data JPA, PostgreSQL, optional Kafka events |
| [`frontend/`](frontend) | Single-page app — Angular 22 (standalone components, signals, zoneless), one app for all three roles |

| Role | What they can do |
|---|---|
| **Claimant** | Register, report an incident, track their claims and their history, answer an officer's request for information, read the decision |
| **Claims officer** | Work queue (oldest first), pick up a claim, start review, request information, approve / reject, mark settled |
| **Manager** | Dashboard (open claims, liability per market, officer performance), see all claims, assign claims to officers |

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

## Run it locally

**You need:** JDK 21 or newer, Maven 3.6.3+ (what Spring Boot 3.4 requires), Node 22.22.3+, 24.15+ or 26+ (the range Angular 22 requires; the frontend was built and
tested with Node 26 and npm 11), and **a PostgreSQL database** (the backend no longer has an embedded one).

**1. Start PostgreSQL.** The backend expects database `claimsdb` with user `claims` / password `change-me` on `localhost:5432`.
The easiest way is the container in this repository:

```bash
docker compose up -d postgres
```

Already have PostgreSQL (in Docker or installed)? Create the user and database in it, or point the backend at yours with the
environment variables `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`: see [DOCKER.md](DOCKER.md) part 9, which has the exact commands.

**2. Start the backend:**

```bash
cd backend
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. It creates its tables in PostgreSQL on first start, and **the data now persists** across
restarts (the two staff accounts below are created only if missing). To start from an empty database, see "Reset the data" in
[DOCKER.md](DOCKER.md) part 6.

**3. Start the frontend** (second terminal):

```bash
cd frontend
npm install
npm start
```

**4. Open <http://localhost:4200>** and sign in.

| Account | Email | Password |
|---|---|---|
| Claims officer (seeded) | `officer@claims.local` | `ChangeMe123!` |
| Manager (seeded) | `manager@claims.local` | `ChangeMe123!` |
| Claimant | register yourself with **Create an account** | your choice (8–72 characters) |

The login page has a "Local demo accounts" panel whose buttons fill in the officer or manager email. Override the seeded password with the `BOOTSTRAP_PASSWORD`
environment variable, or switch the seeded accounts off with `BOOTSTRAP_USERS=false`.

| URL | What |
|---|---|
| `http://localhost:4200` | the application |
| `http://localhost:8080/swagger-ui/index.html` | API documentation (OpenAPI JSON at `/v3/api-docs`) |
| `localhost:5432`, database `claimsdb` | PostgreSQL (user `claims`); with the Docker container: `docker compose exec postgres psql -U claims -d claimsdb` |

> **Why the frontend talks to the backend through a proxy.** The backend has no CORS configuration, so a browser on port 4200
> cannot call port 8080 directly (the browser blocks the cross-origin call). `npm start` proxies `/api` to the backend
> (`frontend/proxy.conf.json`), so always use `http://localhost:4200`. See [ARCHITECTURE.md](ARCHITECTURE.md), part D.

## Run it with Docker (PostgreSQL + backend + frontend + Kafka)

With Docker Desktop installed and ports 4200, 8080, 5432 and 9094 free, one command builds and starts everything, including PostgreSQL
(data kept in a Docker volume) and a Kafka broker that receives the claim events:

```bash
docker compose up --build -d
```

Then open <http://localhost:4200>. **[DOCKER.md](DOCKER.md)** has the full steps: installing WSL 2 and Docker Desktop, checking the install,
watching events arrive in Kafka, every day-to-day command, a Kafka configuration check, and troubleshooting. These Docker files have
not been run yet (see the status note at the top of DOCKER.md).

## Try it end to end

* **`docs/E2E-VALIDATION-FLOW.txt`** — a tick-box script with fixed test data (two claimants, three claims) that exercises every
  role and checks the dashboard numbers at six checkpoints (A-F) and a final state. It takes 25-30 minutes. Start from an **empty database** (PostgreSQL keeps data between runs, so reset it first: [DOCKER.md](DOCKER.md) part 6).
* **Swagger** — `docs/SWAGGER-TEST-GUIDE.txt` explains how to authorise and call each endpoint.
* **Postman** — `backend/postman/Claims-Management.postman_collection.json`: run the requests in collection order; the login
  requests capture the tokens and ids into collection variables.

## The API at a glance

All endpoints except register and login need `Authorization: Bearer <token>`. Errors are RFC 7807 `application/problem+json`.

| Endpoint | Access | Purpose |
|---|---|---|
| `POST /api/auth/register` | public | Create a claimant account; returns a JWT |
| `POST /api/auth/login` | public | Sign in (any role); returns a JWT |
| `GET /api/auth/me` | any | The signed-in user's profile |
| `POST /api/claims` | CLAIMANT | Submit a claim |
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

## Events (transactional outbox)

Every status change writes an audit row **and** an outbox row in the same database transaction. A scheduled relay publishes
unpublished rows as `ClaimStatusChanged` events and marks them published (at-least-once; each event has an `eventId` for
de-duplication; the claim id is the Kafka key so one claim's events stay in order).

| Setting | Default | Meaning |
|---|---|---|
| `app.events.publisher` (`EVENTS_PUBLISHER`) | `log` | `log` writes the event to the application log (no broker needed); `kafka` publishes to Kafka |
| `spring.kafka.bootstrap-servers` | not set | Required when the publisher is `kafka` |
| `app.events.topic` (`EVENTS_TOPIC`) | `claims.claim-status-changed` | Target topic |
| `app.outbox.poll-ms` (`OUTBOX_POLL_MS`) | `5000` | Relay interval |

The Kafka publisher is wired and unit-tested but has **not** been run against a real broker.

## Tests

```bash
cd backend  && mvn test                         # unit + Spring Boot integration tests (in-memory H2 in PostgreSQL mode: no database server needed)
cd frontend && npm test -- --no-watch           # Vitest: 216 unit/component tests
cd frontend && npm run build                    # strict production build
```

Backend tests cover the service rules, the audit recorder, the outbox relay and publishers, validation, and two end-to-end
Spring Boot tests (82 tests). Frontend tests cover the API contract (every verb, path and body),
the interceptor and guards, an exhaustive check of the UI's "who can do what" table against the backend workflow, and each screen.

## Documentation

| File | What is in it |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | System overview; backend design and security; frontend structure; integration decisions (contract, proxy, auth, money); limitations and next steps |
| [AI_JOURNAL.md](AI_JOURNAL.md) | How AI was used: every prompt, what was accepted, challenged or overridden, and what was and was not verified |
| [backend/CLAUDE.md](backend/CLAUDE.md), [frontend/CLAUDE.md](frontend/CLAUDE.md), [CLAUDE.md](CLAUDE.md) | Working rules for AI assistants, per area |
| [DOCKER.md](DOCKER.md) | Install Docker, run the whole system with Kafka in containers, verify events, troubleshoot |
| [docs/E2E-VALIDATION-FLOW.txt](docs/E2E-VALIDATION-FLOW.txt) | Manual end-to-end test script |
| [docs/SWAGGER-TEST-GUIDE.txt](docs/SWAGGER-TEST-GUIDE.txt) | Testing the API through Swagger |

## Known limitations

Listed honestly in [ARCHITECTURE.md](ARCHITECTURE.md), part E. The ones a reviewer will meet first: the schema is created by Hibernate
(`ddl-auto=update`) instead of migrations, and the PostgreSQL setup has not yet run against a real server; no CORS on the backend (hence the proxy), no currency on claims (amounts are shown with their market and the
dashboard total is labelled "unconverted"), and no optimistic locking on the backend.
