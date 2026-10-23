# AI Working Journal

| | |
|---|---|
| **Project** | Claims Platform: `backend/` (Spring Boot, formerly `chubb-claims-backendService`) and `frontend/` (Angular, formerly `claim_management_UI`) |
| **Author** | Kuna Parida |
| **Document purpose** | Record of how AI assistance was used: the prompts that shaped the design, found a defect or changed the repository, what the AI produced, and what was accepted, challenged or overridden, with the reasoning. |
| **Last updated** | 2026-10-23 |

This is the single journal for the whole submission (the backend and frontend journals merged). The author reviews and takes
responsibility for every line in this repository. AI output is treated as a proposal, not a result, until a build, a test run or a manual
check has verified it. Each entry says what was **actually verified** and what was **not**.

**Prompt authenticity:** prompt blocks are reproduced as recorded where the original wording was retained. Where the original wording was not preserved, the entry is explicitly labelled **reconstructed prompt summary (not verbatim)**. This distinction is intentional: it shows the kind of instruction used without presenting newly written wording as an exact historical transcript.

> **How to read the Decision line.** `Decision` is the *author's* call. Where the author has not yet reviewed an entry it says
> `Pending author review`; it is deliberately not pre-filled. What the AI found or got wrong is recorded under *Result* and
> *Corrections*, not disguised as the author's decisions.

## 1. How this journal is kept

| Decision | Meaning |
|---|---|
| **ACCEPTED** | AI output used as generated. |
| **CHALLENGED** | AI output questioned or verified, then kept or changed. |
| **OVERRODE** | The author decided differently from the AI. |

**Prompt inventory:** the log includes the retained substantive backend prompts (1–4), reconstructed prompt summaries for major backend design decisions (D1–D4), and the frontend/integration/packaging prompts (F1–F5). Routine prompts (run this, fix that typo, re-format, "continue") are omitted unless they materially changed the implementation. Where wording was not retained, it is labelled as reconstructed rather than verbatim.

## 2. Summary of the work

| Phase | Entries | Where | Purpose |
|---|---|---|---|
| 1. Reference build | Prompts 1-3 | `C:\project\claims-management` (the author's earlier build, not part of this repository) | A first complete Spring Boot claims system from an architect-style prompt: a working, tested baseline to learn the domain. |
| 2. Backend submission | Prompt 4, D1-D4 | `backend/` (was `C:\project\chubb-claims-backendService`) | Fix the build, then close gaps against the brief: audit history, an outbox for events, claim type and market. |
| 3. Frontend | F1-F3 | `frontend/` (was `C:\project\claim_management_UI`) | `CLAUDE.md` first, then all 19 endpoints integrated into one Angular app for three roles, then a manual end-to-end script. |
| 4. Packaging | F4-F5 | repository root | One repository with one README, ARCHITECTURE and this journal; then Docker and a Kafka configuration check. |

## 3. Key outcomes

- A layered, modular-monolith Spring Boot 3.4 / Java 21 application with JWT security, role-based access and RFC 7807 errors, on an
  in-memory H2 database.
- Workflow rules enforced in the service layer; invalid transitions raise `BusinessException`.
- Audit history and a transactional outbox for status-change events (log publisher by default, Kafka optional).
- `mvn test`: 82 tests, 0 failures (42 before the submission-repository work).
- An Angular 22 UI (zoneless, signals) integrating all 19 endpoints for the three roles in one app: 216 Vitest tests and a clean strict
  production build (about 293 kB initial).
- Both applications, their histories and one set of documents in a single repository, with a Docker Compose stack (backend, frontend,
  Kafka). What has not been run or clicked through is stated in the entries and in `ARCHITECTURE.md` part E.

---

# Part A: Backend Prompt Log

## Phase 1: Reference build (`claims-management`)

### Prompt 1: Build the application

**Prompt**
```text
Act as a Senior Java Architect.
Build a Claims Management System as a Monolithic Spring Boot Application.

Tech stack: Java 21, Spring Boot 3.x, Maven, Spring Web, Spring Data JPA, Spring Validation,
Spring Security, JWT Authentication, PostgreSQL (or H2 for development), Lombok, MapStruct,
OpenAPI/Swagger, JUnit 5, Mockito.

Architecture (package com.claims): controller, service, repository, entity, dto, mapper,
security, exception, validation, config, util.

Entities:
1. Claim: id, claimNumber, policyNumber, claimantName, claimantEmail, incidentDate, description,
   amountClaimed, status, assignedOfficer, createdAt, updatedAt
2. User: id, username, password, role

ClaimStatus enum: SUBMITTED, UNDER_REVIEW, MORE_INFO_REQUIRED, APPROVED, REJECTED, SETTLED

Valid status flow:
SUBMITTED -> UNDER_REVIEW
UNDER_REVIEW -> MORE_INFO_REQUIRED
MORE_INFO_REQUIRED -> UNDER_REVIEW
UNDER_REVIEW -> APPROVED
UNDER_REVIEW -> REJECTED
APPROVED -> SETTLED
Invalid transitions must throw BusinessException.

Roles: ROLE_CLAIMANT, ROLE_OFFICER, ROLE_MANAGER

APIs:
- Claimant: POST /api/claims, GET /api/claims/{id}, GET /api/claims
- Officer: PUT /api/claims/{id}/assign, /review, /request-info, /approve, /reject, /settle
- Manager: GET /api/dashboard/workload, /liability, /performance

Validation: policyNumber mandatory, claimantName mandatory, amountClaimed > 0,
incidentDate cannot be a future date, description minimum 20 characters.

Exception handling: GlobalExceptionHandler, BusinessException, ResourceNotFoundException,
ValidationException, using RFC 7807 Problem Details.

Security: JWT authentication, Spring Security, role-based access.
Swagger: enable OpenAPI and provide API documentation.
Database: PostgreSQL, schema created automatically through JPA, with an application.yml.

Deliverables: pom.xml, entities, repositories, DTOs, services, controllers, security,
JWT implementation, exception handling, Swagger config, README, Postman collection.

Follow SOLID, clean code, layered architecture, constructor injection, proper logging,
and Java 21 best practices.
```

**Result:** Full project: entities, workflow rules, APIs, JWT security, error handling, Swagger, README and the first
Postman collection.

**Decision: ACCEPTED** as a baseline only. The AI had no Maven in its environment, so nothing was compiled until Prompt 4.

**Verification:** deferred to Prompts 2 and 4.

### Prompt 2: Unit tests

**Prompt**
```text
Generate complete JUnit 5 and Mockito test cases for the Claims Management Application.
Coverage target: 90%+.
Generate tests for ClaimService: createClaim(), assignClaim(), requestMoreInfo(),
approveClaim(), rejectClaim(), settleClaim().
```

**Result:** `ClaimServiceTest`, plus tests for the dashboard, authentication, JWT, validators, exception handler,
controllers and an end-to-end workflow test.

**Decision: ACCEPTED** after the AI reviewed its own tests and fixed a Mockito mistake before the first run (mocks
created inside a `thenReturn(...)` call cause `UnfinishedStubbingException`).

**Verification:** verified build in the reference project: 153 tests passed, 99.4% line coverage (JaCoCo gate at 90%).

### Prompt 3: Move the project and make H2 the default database

**Prompt**
```text
I moved the repo to C:\project; use this location from now on.
I don't have a database installed, so make H2 the default database.
Keep the PostgreSQL configuration, but commented out.
```

**Result:** `application.yml` uses in-memory H2 by default with the PostgreSQL block commented out. The H2 profile file
was removed and the README updated.

**Decision: OVERRODE** the AI's default (PostgreSQL) because of the author's environment constraint (no local database). The
author
later tried switching to PostgreSQL and did not keep it; H2 is the database of this repository.

**Verification:** application starts with no external database.

## Phase 2: Backend submission (`backend/`)

### Prompt 4: Maven build error in the submission repository

**Prompt**
```text
(pasted) [ERROR] Failed to execute goal ...maven-surefire-plugin:3.5.3:test ... on project claims-management
See C:\project\chubb-claims-backendService\target\surefire-reports for the individual test results.
Getting this error while trying maven clean and install.
```

**Result:** The AI read `target/surefire-reports`. Seven tests failed with "Mockito cannot mock this class": the Byte
Buddy version managed by Spring Boot 3.4.5 does not support JDK 27. It added `byte-buddy.version=1.18.14` to `pom.xml`.

**Decision: ACCEPTED**, because the cause was reproduced and the fix confirmed on the same JDK 27 that failed.

**Verification:** `mvn clean install` passed (42 tests at that time).

---

# Part B: Backend Design Decisions

## D1. Audit history and events (transactional outbox)

**Reconstructed prompt summary (not verbatim)**
```text
Review the claims workflow and propose a reliable way to record claim status history and publish status-change events. Compare publishing directly to Kafka with a transactional outbox. Keep audit records and outbox rows in the same database transaction, define delivery guarantees and ordering, and support a logging publisher by default with Kafka as an optional publisher.
```

- **Question (from the brief):** what is synchronous and what is event-driven?
- **AI proposal:** `claim_status_history` and `outbox_events` tables written in the same transaction as the status
  change; a scheduled relay that publishes unpublished rows; a log publisher by default and an optional Kafka publisher
  (`app.events.publisher=kafka`).
- **Challenges and findings:**

  | Challenge | Outcome |
  |---|---|
  | Why not send to Kafka directly from the service? | Dual-write problem: commit and send can disagree. The outbox avoids lost or phantom events. |
  | What delivery guarantee results? | At-least-once. Every event carries an `eventId` for consumer de-duplication. |
  | Can auditing run outside a transaction? | No. `ClaimAuditRecorder.record` uses `Propagation.MANDATORY`. |
  | How is ordering kept? | The relay stops at the first failure to preserve per-claim order; the Kafka record key is the claim id. |

- **Decision: CHALLENGED, then ACCEPTED.**
- **Known limit:** the Kafka publisher is unit-tested with a mocked template. The Docker stack (F5) gives it a real broker to run against;
  no result of that run is recorded yet.

## D2. Claim type and market

**Reconstructed prompt summary (not verbatim)**
```text
Review the claims assessment requirements and identify the additional claim classification fields needed for reporting. Add claimType and market to claim submission and persistence, validate them consistently, update the API documentation and tests, and break liability reporting down by these dimensions. Do not invent a fixed list of markets if the requirements do not define one.
```

- **AI proposal:** `claimType` (MOTOR, PROPERTY) and `market` as a 2-letter code; liability broken down by both.
- **Decision: OVERRODE** a `Market` enum. The set of markets is not known, so an enum would invent business data.
- **Breaking change:** both fields are required on submission. The Postman collection and all existing tests were
  updated in the same change.

## D3. Architecture: modular monolith with REST plus outbox

**Reconstructed prompt summary (not verbatim)**
```text
Recommend an architecture for the claims assessment that can be completed within the assessment time limit. Explain whether a modular monolith or microservices are appropriate, how REST APIs and the event outbox fit together, and which capabilities could be extracted later if scale or ownership boundaries justify it.
```

- One bounded context, one transaction per claim change, within a 2-3 hour budget.
- Split candidates for the future: notifications and reporting, connected by the `ClaimStatusChanged` event.
- Full reasoning is in `ARCHITECTURE.md` part B.

## D4. Verification before trust

**Reconstructed prompt summary (not verbatim)**
```text
Verify the implementation instead of assuming the generated code is correct. Run the Maven verification suite, inspect failures and test reports, fix the root causes, replay the documented claim lifecycle against a fresh application instance, and report precisely which checks passed and which remain unverified.
```

- **AI mistake caught:** while writing the new service tests, the AI first saved an unfinished placeholder test file and
  then rewrote it. Only a green build is counted as evidence.
- **Checks run:**
  - `mvn clean verify` on JDK 27: 82 tests, 0 failures (previously 42).
  - End-to-end replay of the Postman flow against a freshly started app: every step returned the expected status
    (200/201/400/403/409); history showed all six transitions; outbox events appeared in the log.
  - Context tests prove the publisher is `LoggingEventPublisher` by default and `KafkaEventPublisher` when switched.

---

# Part C: Frontend and Packaging Prompt Log

Written first in the frontend repository, then carried over when the two repositories were combined (F4). Paths inside the entries are the
original ones and are left as history: `C:\project\claim_management_UI` is now `frontend/`, `C:\project\chubb-claims-backendService` is now
`backend/`.

| Item | Where | Notes |
|---|---|---|
| **Assessment brief** | `V2_FullStack_candidate_assessment_brief.pdf` (Chubb APAC) | Backend first; "a well-built backend with a partial frontend will outscore a rushed attempt at both". One Angular app serves claimants, claims staff and managers. |
| **Swagger** | `http://localhost:8080/swagger-ui/index.html` | Source of truth for every request and response contract. |

## F1: Write CLAUDE.md and the journal before integrating

**Prompt (cleaned-up reconstruction; not verbatim)**
```text
I am working on a Chubb APAC Full Stack Developer Assessment.

Backend implementation is already completed and fully working.

Backend Location: C:\project\chubb-claims-backendService

Frontend Location: C:\project\claim_management_UI

Backend Technology:

Java 21
Spring Boot
Maven
JWT Authentication
REST APIs
Kafka Event Publishing
Swagger/OpenAPI
Swagger URL: http://localhost:8080/swagger-ui/index.html

Please use the Swagger specification as the source of truth for all request and response contracts.
```

> **Authenticity note:** the original message was not preserved word-for-word. This is a cleaned-up reconstruction of its key requirements, not an exact transcript.

**Result:** `frontend/CLAUDE.md` (purpose, tooling, the full backend contract, the status workflow, the UI action table, architecture
rules) and the UI's first journal. No code was written and the backend was not modified.

**Findings that shape the integration** (the AI read the backend controllers, DTOs, security config and `ClaimService` before writing)

1. **The backend has no CORS configuration.** A browser app on `:4200` calling `:8080` is blocked, so the UI needs the Angular dev proxy
   and relative `/api` URLs. Not fixed by touching the backend.
2. **No `allowedActions`.** The UI needs one central action table (status × role × assignment) mirroring `ClaimService`; the server stays
   the authority, so 403/404/409 must be handled.
3. **`pickup` and `assign` do not change the status.** The claim stays `SUBMITTED` until the officer calls `review`.
4. **No "list officers" endpoint.** `GET /api/dashboard/performance` returns every officer and can feed the manager's assign picker.
5. **Money has no currency.** `amountClaimed` is a bare number, `market` a 2-letter code, and the dashboard totals add across markets.
   The UI shows per-market lines first and labels the grand total as unconverted.
6. Ids are numeric; the claim list is role-scoped (an officer's `GET /api/claims` is only the claims assigned to them).

**Corrections**

* The AI had told the author that Node.js was not installed. It is (v26.3.1); the AI's own shell did not have it on its PATH.
* Ports 8080 and 4200 were occupied by servers the AI had started for an unrelated earlier build, so the Swagger link would have shown the
  wrong API. The AI stopped only its own processes.

**Verification:** the contract in `CLAUDE.md` comes from reading the backend code, not from calling it; the backend was not started in that
step.

**Decision:** `Pending author review`

## F2: Integrate the backend APIs into the UI

**Prompt:** `please procced` (the answer to the AI's plan: proxy, models and API service, then login and guards, then the three role screens;
`npm install` and starting the backend with Maven were approved separately).

**Result** (all under `frontend/src/app`)

* `core/`: `models.ts` (types mirroring the DTOs), `api.service.ts` (the only file with URLs), `auth.service.ts`, `auth.interceptor.ts`,
  `guards.ts`, `problem.ts`, `claim-actions.ts` (the one action table).
* `shell/` (role navigation), `features/` login, register, claims (list, new-claim, claim-detail), queue, dashboard, and `shared/`.
* `proxy.conf.json` wired into `angular.json`; global styles with light and dark tokens.

**Design decisions**

* **Dev proxy, not a backend change.** Proven both ways against the running backend: login through `:4200/api` works; a direct cross-origin
  preflight to `:8080` returns 401 with no CORS headers, which a browser blocks.
* **One action table**, tested against an independent copy of the backend's `TRANSITIONS` map (every status × role × assignment); every
  action still handles 403/404/409 by showing the server's `detail` and reloading the claim.
* **Money shows a market, never a currency symbol**; the dashboard leads with per-market liability and labels the grand total
  "unconverted, mixed markets".

**Corrections**

1. **A real component bug, found by a unit test.** In the work queue, losing a pick-up race (409) set the error message and the reload
   that followed cleared it, so the officer would never learn why the pick-up failed. Fixed with `load(keepError)`.
2. **The first full test run had four failures, three of them mistakes in the specs** (`TestBed.inject` before configuring the module,
   flushing a request `forkJoin` had cancelled, a spec that did not register the interceptor); the fourth was the bug above.
3. `GET /api/auth/me` was defined but unused; it now re-validates the stored session on app load.

**Verification**

* **Contract:** live `/v3/api-docs` against `ApiService`: 19 endpoints each way, none missing.
* **Live lifecycle against the real backend** through the dev proxy: 28 checks (register, login ×3, 401, 400 field errors, 403 email
  mismatch, create, queue, pick up, review, request info, 409, claimant answer, approve, settle, history, 404 for another claimant,
  dashboards, 403 for an officer on the dashboard, manager assigns).
* **Browser walkthrough:** register, report an incident, officer picks up and reviews, requests information, claimant answers, history,
  manager dashboard with the unconverted-total label.
* **Unit tests:** 212 at the end of this entry (216 after F3); strict production build clean (292.6 kB initial).
* **Mutation check:** making the action table offer SETTLE on `UNDER_REVIEW` failed 6 tests; restored.

**Not verified:** in a real browser, officer approve/reject/settle, the manager's assign picker, dark mode and narrow layouts (the first
three are covered by unit tests and the scripted API run, not by clicking); accessibility beyond labels, roles and focus styles.

**Decision:** `Pending author review`

## F3: A manual end-to-end script

> **Reconstructed entry.** The earlier journal pointed to this entry but did not contain it. The AI rewrote it on 2026-10-23 from its own
> record of the session and checked what it could against files that still exist (`docs/E2E-VALIDATION-FLOW.txt`, `problem.ts` and its
> spec). The scripted-run figures cannot be re-checked from a file.

**Prompt:** `okay now for validating the end to end flow please provide me the whole flow in text file so I can follow and check everything`

**Result:** `docs/E2E-VALIDATION-FLOW.txt`, a tick-box script with fixed test data (two claimants, three claims) covering every role, with
dashboard checkpoints A to F and a final state, a "things that look odd but are not bugs" list, a diagnosis table and a results sheet.

**Corrections**

1. **A real UI defect found while preparing it.** With the backend stopped, the Angular dev proxy answers `502 Bad Gateway` with an empty
   body, not status 0, so the UI would have shown a generic failure. `problem.ts` now maps 502, 503 and 504 to "Cannot reach the server…"
   (tests added: 212 to **216**).
2. **The guide describes what the backend really does:** settling replaces the approval note in the claim's decision field (the earlier
   note stays in the history); a token that outlives its account gets a 401 and the UI signs out.
3. The first draft said "Node 20 or newer"; Angular 22 needs 22.22.3+, 24.15+ or 26+. Fixed in F4.

**Verification:** before the guide was written the AI ran the whole flow against the real backend (fresh database, through the dev proxy):
**45 checks passed**, and the numbers in the EXPECT lines come from that run. **Not verified:** the AI did not click through every step in a
browser; only one officer is seeded, so a race between two officers cannot be tried by hand (the automated tests cover it).

**Decision:** `Pending author review`

## F4: One repository, one set of documents

**Prompt:** `now I need to submit both project in once so I need both backend and UI in one project and for both we need one README , ARCHITECTURE and AIJOURNAL file is this possible?`

**Result:** a new repository with the backend under `backend/` and the frontend under `frontend/`, both histories imported with
`git subtree add`; one root `README.md`, `ARCHITECTURE.md` and this journal; the test guides moved to `docs/`; the `CLAUDE.md` files updated
and a root `CLAUDE.md` added. The two original repositories were not modified.

**Corrections**

1. **The backend repository had a Maven cache committed** (`.build-cache/`, 151 jars, 82 MB) that a plain clone could not check out on
   Windows (paths too long). It was removed from every imported backend commit with `git filter-branch`, so the backend's **commit
   hashes differ from the original repository's**; messages, authors, dates and the final file contents are the same.
2. **A command chain stopped halfway** on those long paths, so only its read-only commands had run; the import was redone with a
   no-checkout clone and sparse checkout, and the originals were checked to be untouched. Two shell commands run in parallel once shared a
   working directory and reported the wrong repository; every later command used `git -C <path>`.
3. **Mistakes in the AI's first drafts of the new documents**, found by checking them against the source: Node 20+ (see F3), a login page
   that "signs in with one click" (the demo panel only fills in the email), and lists that "load one page at a time" (they are paged,
   10 per page).

**Verification:** the imported `frontend/` tree is identical (same git tree hash) to the original HEAD, and `backend/` to the original
without `.build-cache/`. Built and tested from the combined repository: backend `mvn -o test` 82 tests, 0 failures; frontend 216 tests in
15 files; `npm run build` clean. **Not verified:** a first-time `npm install` in the combined `frontend/`.

**Decision:** `Pending author review`

## F5: Dockerize, and check the Kafka configuration

**Prompt:** `you dockerize it and check kafka configuration and also create an md file where you need to list out the steps to install all the things in docker and run all the commands also to make the application succesful up`

**Result**

* `backend/Dockerfile` (Maven build on JDK 21, then a JRE-only alpine image, non-root), `frontend/Dockerfile` (Node 24 build, then nginx),
  `frontend/nginx.conf` (serves the app, falls back to `index.html`, forwards `/api/` to the backend so the missing CORS configuration never
  matters), a `.dockerignore` in each app folder, and `docker-compose.yml`: single-node Kafka 3.9 (KRaft), a `kafka-init` job that creates the
  topic, the backend with the Kafka publisher switched on by environment variables, and the frontend on port 4200.
* `DOCKER.md`: installing WSL 2 and Docker Desktop, build and run, watching events arrive in Kafka, everyday commands, a Kafka
  configuration table, troubleshooting and other ways to run.
* The backend source was not changed by this entry.

**Kafka configuration check: findings**

1. `spring.kafka.bootstrap-servers` is commented out in `application.properties`, so compose supplies the broker address through
   `SPRING_KAFKA_BOOTSTRAP_SERVERS`.
2. The application never creates its topic; `kafka-init` does, and broker auto-creation is off so a wrong topic name fails loudly.
3. No producer settings are needed: Spring Boot's String serializers match `KafkaTemplate<String, String>`; the key is the claim id.
4. Only claim creation and the status transitions write outbox rows; `pickup` and `assign` do not (checked in `ClaimService`).
5. **A weakness, not fixed:** with the broker down, `send()` can wait for Kafka's default `max.block.ms` (60 s) before the code's own 10 s
   acknowledgement timeout applies, holding the relay thread and its transaction. A suggested setting is in `DOCKER.md`.
6. Nothing consumes the events yet.

**Corrections:** the AI tried to run a local production build only to learn the Angular output folder, and the author interrupted that
command; the folder (`dist/claim-management-ui/browser`) was confirmed from the installed builder's schema instead. A Debian-based Node
image was chosen over alpine as a judgement (the lock file was generated on Windows), not a tested result.

**After this entry** the author committed the Docker files and then changed the compose file and `SecurityConfig` (commit `f6236c1`: a
restart policy for the backend, and the H2 console enabled and reachable from outside the container). Those edits are the author's.

**Verification:** the Kafka code and configuration were read in full (`application.properties`, `KafkaEventPublisher`,
`OutboxPublisher`, `pom.xml`, `SchedulingConfig`). **Not verified by the AI:** Docker was not available to it, so no image was built and no
container started. No result of running the stack is recorded here; `DOCKER.md` part 5 is the procedure.

**Decision:** `Pending author review`

---

# Part D: Open Questions for the Backend

The frontend does not change the backend. These are things the author may want to change there, or accept as limitations and explain.

| # | Question | Impact on the UI |
|---|---|---|
| 1 | Add CORS (or accept a dev-proxy-only setup)? | **Confirmed needed:** a direct call from `:4200` gets a 401 with no CORS headers. The UI works only through the dev proxy (or nginx in Docker) or a same-origin deployment. |
| 2 | Expose an officers list (or accept `/dashboard/performance` as the source)? | Manager assign picker. |
| 3 | Add a currency (or a market→currency mapping) to claims and the liability report? | Without it the UI cannot show correct money, and grand totals mix currencies. |
| 4 | Return allowed actions per claim? | Today the UI mirrors the workflow rules itself, which can drift from the backend. |
| 5 | Reassigning `REJECTED`/`SETTLED` claims is not forbidden (`backend/CLAUDE.md` notes this). | The UI simply does not offer it. |

---

# Part E: Known Limitations

Backend:

| Area | Current state |
|---|---|
| Concurrency | No optimistic locking (`@Version`). |
| Messaging | The Kafka publisher is unit-tested with a mocked template; no result of running it against a real broker is recorded. |
| Database | In-memory H2 with `ddl-auto=update` and no migration tooling: **all data is lost on restart**. A PostgreSQL example is only a comment in `application.properties` (no driver is a dependency). The H2 console is enabled and open to anyone who can reach the port (local development only). |
| Secrets | Default JWT secret and seeded-account password are for local use only. |
| Quality metrics | Code coverage is not measured (no JaCoCo plugin). |

Frontend and integration (full table, with a next step for each, in `ARCHITECTURE.md` part E): no CORS on the backend, so the UI needs the dev
proxy or a same-origin gateway; the action table is a copy of backend rules; no currency on claims; the JWT is kept in `localStorage`;
officer approve/reject/settle, the manager assign picker, dark mode and narrow layouts were not clicked through in a browser; no
browser-level automated tests.

---

# Part F: Open Items

These are the author's to write; the AI has not filled them in because it would have to invent them.

1. **Pre-existing commits.** The backend repository already had commits before the submission work (`Initial commit` to `pagination
   added`). State which parts were AI-generated, which were written by hand, and how they relate to the Phase 1 build.
2. **F1 prompt text.** If an exact transcript is available, replace the clearly labelled reconstruction with it; otherwise keep the authenticity note.
3. **Decisions for F1 to F5** are all `Pending author review`. Choose ACCEPTED, CHALLENGED or OVERRODE for each, with your reason.
4. **F3 was reconstructed.** Read it and correct anything that does not match what you remember of that session.
5. **Backend commit hashes.** The backend history here has the same commits, messages, authors and dates as the original, but different
   hashes, because `.build-cache/` was removed from every commit (see F4). That is why `git log` does not match the original repository.
6. **Docker and Kafka run (F5).** Follow `DOCKER.md`, and write what happened, including any fix needed, into F5. Decide whether to add
   `spring.kafka.producer.properties.max.block.ms=10000` to the backend.
7. **Fresh-clone check.** Clone the repository somewhere clean, run `npm install` in `frontend/`, and follow `README.md` once.

---

# Appendix: Template for New Entries

```markdown
## F6: <short title>

**Prompt:** <what was typed, or "the author's edited text">

**Result:** what the AI produced or changed; list files.

**Corrections:** anything the AI got wrong, or the author challenged, and how it was resolved, or "none".

**Verification:** what was actually run or checked (build, tests, manual) and what was NOT.

**Decision:** ACCEPTED | CHALLENGED | OVERRODE, with the author's reasoning, or `Pending author review`.
```

Only add an entry when the prompt set the design, found or fixed a defect, changed the repository, or the AI was corrected.
