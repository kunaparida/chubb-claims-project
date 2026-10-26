# AI Working Journal

| | |
|---|---|
| **Project** | Claims Platform: `backend/` (Spring Boot, formerly `chubb-claims-backendService`), `frontend/` (Angular, formerly `claim_management_UI`) and `notification-service/` (Kafka consumer that sends email) |
| **Author** | Kuna Parida |
| **Document purpose** | A record of how AI was used: the prompts that shaped the design, found a defect or changed the repository; what the AI produced; and what was accepted, challenged or overridden, and why. |
| **Last updated** | 2026-10-26 |

This is the single journal for the whole submission. The author reviews and takes responsibility for every line in this repository. AI output is treated as a
proposal until a build, a test run or a manual check has proved it. Each entry says what was **actually checked** and what was **not**.

**Prompts.** A prompt block is copied exactly where the original wording was kept. Where it was not kept, the entry says **reconstructed prompt summary (not verbatim)**, so a newly written sentence is never passed off as an exact quote.

> **The Decision line** is the *author's* call. Until the author has reviewed an entry it says `Pending author review`. What the AI found or got wrong is written under *Result* and *Corrections*, not hidden inside the author's decisions.

> **Section numbers in older entries.** Entries F1 to F10 mention parts of `ARCHITECTURE.md` by their old letters (A.1, B.3, B.9, B.10, C.7, D.2, "part E"). The document has since been rewritten with numbered sections. Use this map:
> decisions table (A.1) → section 2; backend design (B) → sections 4 to 8; claim documents (B.9) → 4.3; events (B.3) → 7.3; notification service (B.10) → 7.4; testing (B.8, C.8) → 11;
> frontend (C) → 9; integration decisions and the proxy (D, D.2) → 10; known limitations (part E) → 12.

## At a glance

**The moments where the human or the evidence changed what the AI produced** (details in the entries named):

| # | What happened | Entry |
|---|---|---|
| 1 | Reading the backend before writing any UI code exposed a gap the brief never mentions: **no CORS configuration**. The UI was built around a dev proxy instead of editing the backend. | F1, F2 |
| 2 | The AI proposed a `Market` enum; the author **overrode** it with a validated two-letter code, because the list of markets is business data nobody owns. | D2 |
| 3 | The outbox proposal was **challenged** ("why not send to Kafka directly from the service?"); the answer (commit and send can disagree) held up, and the delivery guarantee and ordering rules were written down before it was kept. | D1 |
| 4 | A unit test found a **real bug**: when an officer lost a pick-up race, the reload wiped the error message, so nobody would learn why. | F2 |
| 5 | Writing the end-to-end guide exposed a **second real defect**: a stopped backend shows as `502` through the dev proxy, which the UI did not recognise. | F3 |
| 6 | The AI's own wrong statements were caught and corrected: "Node is not installed", a login page that "signs in with one click" (it only fills in the credentials), lists that "load one page at a time". | F1, F4, F8 |
| 7 | Seven tests failed on JDK 27; the cause (Byte Buddy) was **reproduced from the surefire reports**, not guessed. | Prompt 4 |
| 8 | The AI converted the database to PostgreSQL; the author **overrode** it and kept H2. | F6 |
| 9 | Reviewing the project against a scoring rubric found dead code, an unauthenticated database console and undocumented endpoints; all three were fixed and **verified on the running app**. | F7 |
| 10 | After a UI redesign, looking at the **running app** found what 250 unit tests could not: a blank logo on phone screens (two logos shared one gradient id), a header four rows tall, wrapping claim numbers. All fixed and re-checked. | F8 |
| 11 | A request for an *optional upload* had nothing to land on, because the API could not store a file, so it was built from end to end. The file type is judged from the file's **first bytes**, not its name. Using the running app then found a wrong download header, an unstyled button and a cramped phone layout. | F9 |
| 12 | For the notification service the AI found that the Kafka producer already existed, and that the event **had no claimant email** to send to. All tests were green, yet the first run of the real applications together showed the consumer group setting was being ignored. It was fixed with a test that was first seen to fail. | F10 |
| 13 | After the Kafka commit, a check of the whole project found that the README was an older copy (old test counts, no notification service), that `ARCHITECTURE.md` pointed to three diagram files that did not exist, and that many documents used section letters that no longer existed. All fixed. | F11 |

**Evidence rather than assertion:** 118 backend tests (97.1% line coverage, measured by JaCoCo), 76 notification-service tests (a real Kafka broker and SMTP server run inside them), 312 frontend tests, a live run of the real applications together, a 28-check and a
45-check scripted run against the real backend, a browser walkthrough, and a mutation check that deliberately broke a rule to prove the
tests notice. What was *not* run (Docker, Kafka against a broker, the real file-chooser dialog, some screens in a browser) is listed in each entry.

**Who did what**

| | |
|---|---|
| **The author decided** | Backend first and frontend second (the brief's priority); H2 as the database; one repository with one set of documents; Docker with Kafka; to drop the PostgreSQL attempt and keep H2; to keep this journal to the prompts that matter; to add a Kafka-consuming notification service that emails the claimant (and allowed the download of the mail libraries it needed); and edited the compose file and `SecurityConfig` by hand. |
| **The AI (Claude Code) did** | Read the sources, proposed designs, wrote code, tests and documents, ran builds, tests and live checks, and reported what it could not verify. |
| **Guardrails written down** | Each area's `CLAUDE.md`: commit only when asked, ask before installing or downloading, never edit the backend from UI work, never invent the author's decisions, keep documents true to the code. |

## 1. How this journal is kept

| Decision | Meaning |
|---|---|
| **ACCEPTED** | AI output used as generated. |
| **CHALLENGED** | AI output questioned or verified, then kept or changed. |
| **OVERRODE** | The author decided differently from the AI. |

**Prompt inventory:** the log includes the retained substantive backend prompts (1–4), reconstructed prompt summaries for major backend design decisions (D1–D4), and the frontend/integration/packaging/review prompts (F1–F11). Routine prompts (run this, fix that typo, re-format, "continue") are omitted unless they materially changed the implementation. Where wording was not retained, it is labelled as reconstructed rather than verbatim.

**Decision lines.** For entries the author has not yet written up, the AI adds a *Proposed* verdict with its reasons, taken from the record (what was checked, what was corrected). It is a suggestion only: the author confirms it or replaces it with their own words.

## 2. Summary of the work

| Phase | Entries | Where | Purpose |
|---|---|---|---|
| 1. Reference build | Prompts 1-3 | `C:\project\claims-management` (the author's earlier build, not part of this repository) | A first complete Spring Boot claims system from an architect-style prompt: a working, tested baseline to learn the domain. |
| 2. Backend submission | Prompt 4, D1-D4 | `backend/` (was `C:\project\chubb-claims-backendService`) | Fix the build, then close gaps against the brief: audit history, an outbox for events, claim type and market. |
| 3. Frontend | F1-F3 | `frontend/` (was `C:\project\claim_management_UI`) | `CLAUDE.md` first, then all 19 endpoints integrated into one Angular app for three roles, then a manual end-to-end script. |
| 4. Packaging | F4-F5 | repository root | One repository with one README, ARCHITECTURE and this journal; then Docker and a Kafka configuration check. |
| 5. Review | F6-F7 | `backend/`, root | A PostgreSQL attempt the author reversed; then a review of the whole project against a scoring rubric, with fixes. |
| 6. UI redesign | F8 | `frontend/` | A professional look (logo, design system, light and dark), and a new report form: market list, 7-day date window, generated policy number. |
| 7. Documents | F9 | `backend/`, `frontend/`, root documents | Optional document upload (three new endpoints, a section on the report form, a Documents box on the claim page) and a message for every missing or invalid field. Takes the API from 19 to 22 endpoints. |
| 8. Notifications | F10 | `notification-service/`, `backend/` (event), compose, documents | A separate service that consumes the status-change events from Kafka and emails the claimant: retries, a dead-letter topic, duplicate protection, a local inbox (Mailpit) in Docker. The event gains the claimant's email and name. |
| 9. Final check | F11 | root documents, `docs/diagrams/` | Everything re-tested from the committed state; README brought up to date, three missing diagrams added, document cross-references fixed, wording made simpler. |

## 3. Key outcomes

- A layered, modular-monolith Spring Boot 3.4 / Java 21 application with JWT security, role-based access and RFC 7807 errors, on an
  in-memory H2 database.
- Workflow rules enforced in the service layer; invalid transitions raise `BusinessException`.
- Audit history and a transactional outbox for status-change events (log publisher by default, Kafka optional).
- `mvn test`: 118 tests, 0 failures (42 before the submission-repository work), with a JaCoCo coverage report: 97.1% of lines, 86.9% of branches.
- Every endpoint documented in OpenAPI (enforced by a test); `POST /api/claims` answers `201` with a `Location` header; the H2 console is off by default.
- Optional claim documents (PDF, JPG or PNG; 5 files of 5 MB; type judged from the file's contents), visible to whoever can see the claim.
- A separate notification service (`notification-service/`): it consumes the status-change events from Kafka and emails the claimant, with retries, a dead-letter
  topic and duplicate protection. Its tests run a real Kafka broker and SMTP server in the JVM, and the real applications were run together once outside Docker.
- An Angular 22 UI (zoneless, signals) integrating all 22 endpoints for the three roles in one app: 312 Vitest tests and a clean strict
  production build (about 316 kB initial).
- All three applications, their histories and one set of documents in a single repository, with a Docker Compose stack (backend, frontend,
  notification service, Mailpit, Kafka). What has not been run or clicked through is stated in the entries and in `ARCHITECTURE.md` section 12.

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
- **Known limit:** the Kafka publisher is unit-tested with a mocked template. It was later run against a real broker started inside a JVM
  (F10, 2026-10-26); the Docker stack (F5) is still the only way to run it against the compose broker, and no result of that run is recorded yet.

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

**Decision:** `Pending author review`. *Proposed by the AI:* **ACCEPTED**: `CLAUDE.md` was used as written after the AI corrected its own
Node and port statements. If you questioned the findings yourself before accepting them, say CHALLENGED.

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

**Decision:** `Pending author review`. *Proposed by the AI:* **CHALLENGED**: nothing was kept on the AI's word; tests, a live run and a browser
walkthrough found a real bug and three spec mistakes, and they were fixed first.

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

**Decision:** `Pending author review`. *Proposed by the AI:* **ACCEPTED**, with the one defect the guide exposed (502 handling) fixed in the UI.

## F4: One repository, one set of documents

**Prompt:** `now I need to submit both project in once so I need both backend and UI in one project and for both we need one README , ARCHITECTURE and AIJOURNAL file is this possible?`

**Result:** a new repository (`chubb-claims-platform`) with the backend under `backend/` and the frontend under `frontend/`, both
histories imported with `git subtree add`; one root `README.md`, `ARCHITECTURE.md` and this journal; the test guides moved to `docs/`; the `CLAUDE.md` files updated
and a root `CLAUDE.md` added. The two original repositories were not modified.

**Corrections**

1. **The backend repository had a Maven cache committed** (`.build-cache/`, 151 jars, 82 MB) that a plain clone could not check out on
   Windows (paths too long). It was removed from every imported backend commit with `git filter-branch`, so the backend's **commit
   hashes differ from the original repository's**; messages, authors, dates and the final file contents are the same.
2. **A command chain stopped halfway** on those long paths, so only its read-only commands had run; the import was redone with a
   no-checkout clone and sparse checkout, and the originals were checked to be untouched. Two shell commands run in parallel once shared a
   working directory and reported the wrong repository; every later command used `git -C <path>`.
3. **Mistakes in the AI's first drafts of the new documents**, found by checking them against the source: Node 20+ (see F3), a login page
   that "signs in with one click" (the demo panel fills in the email and password but does not sign in; this correction was itself imprecise and was fixed in F8), and lists that "load one page at a time" (they are paged,
   10 per page).
4. **The history did not travel with the folder.** The repository later pushed to GitHub (`chubb-claims-project`) was made from a copy
   of the working folder and starts with a single commit holding all 147 files, so the imported development history exists only in
   `chubb-claims-platform`. Found on 2026-10-24 (F7); the author's decision is open (Part F).

**Verification:** the imported `frontend/` tree is identical (same git tree hash) to the original HEAD, and `backend/` to the original
without `.build-cache/`. Built and tested from the combined repository: backend `mvn -o test` 82 tests (the count at that time), 0 failures;
frontend 216 tests in 15 files; `npm run build` clean. **Not verified:** a first-time `npm install` in the combined `frontend/`.

**Decision:** `Pending author review`. *Proposed by the AI:* **CHALLENGED**: the AI's first documents were checked against the source and three
wrong statements were removed before they were kept.

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
6. Nothing consumes the events yet. (F10 later added the notification service, which does.)

**Corrections:** the AI tried to run a local production build only to learn the Angular output folder, and the author interrupted that
command; the folder (`dist/claim-management-ui/browser`) was confirmed from the installed builder's schema instead. A Debian-based Node
image was chosen over alpine as a judgement (the lock file was generated on Windows), not a tested result.

**After this entry** the author committed the Docker files and then changed the compose file and `SecurityConfig` (commit `f6236c1`: a
restart policy for the backend, and the H2 console enabled and reachable from outside the container). Those edits are the author's. F7
later made the console opt-in (`H2_CONSOLE_ENABLED`), in the properties file and in the compose file.

**Verification:** the Kafka code and configuration were read in full (`application.properties`, `KafkaEventPublisher`,
`OutboxPublisher`, `pom.xml`, `SchedulingConfig`). **Not verified by the AI:** Docker was not available to it, so no image was built and no
container started. No result of running the stack is recorded here; `DOCKER.md` part 5 is the procedure.

**Decision:** `Pending author review`. *Proposed by the AI:* **CHALLENGED**: the Kafka configuration was checked in the code, one weakness was
found and deliberately left for the author, and nothing is claimed about a run.

## F6: Convert H2 to PostgreSQL (attempted, then reversed by the author)

**Prompt** (verbatim; the second message arrived while the work was running)
```text
convert now db h2 to postgress and change related thing in all files
```
```text
btw I have potgressql in docker
```

**Result:** the AI made PostgreSQL the runtime database: the driver as a runtime dependency, the datasource read from `DB_URL`, `DB_USERNAME`
and `DB_PASSWORD`, H2 moved to test scope in PostgreSQL compatibility mode, a `postgres` service with a volume and health check in the
compose file, and every document updated.

**Verification at the time:** 82 backend tests passed on H2 in PostgreSQL mode (in a scratch copy), and the PostgreSQL schema Hibernate
generates was read and found valid. **It never ran against a real PostgreSQL server.** The AI said so then, and could not see the author's
Docker container (name, port and credentials unknown to it).

**What the author did:** removed the conversion and continued with H2 ("I removed postgres due to some reason I like to continue with h2
database"). None of the PostgreSQL changes are in the repository; the documents describe H2.

**Decision: OVERRODE**: the author kept H2. Reason: `(author to add: Part F)`.

## F7: Review against a scoring rubric, and fix what it found

**Prompt** (verbatim; the first message came with a screenshot of a sample score table for full-stack candidates, not stored here)
```text
this is the project score validation sample please analyse and provide the tips what we need to taken care so we got a good score
```
```text
do items 4, 5, 6 and clean up the journal and also make the AI_JOURNAL changes for achieve good score because AI Collaboration have 25 point
```

**What the AI read first:** the sample table (135 points: backend 90, of which 25 are AI collaboration; frontend 45; the three sample
candidates scored 0 of 45 on the frontend and lost most of their other points on AI collaboration, planning and clean architecture), the
full-stack assessment brief, and the repository itself.

**Findings in the repository** (each checked, not assumed)

1. `SecurityConfig` still held the previous filter chain as commented-out code.
2. The H2 console was enabled, open to other hosts and reachable without a token.
3. No OpenAPI annotations at all (no summaries, descriptions or examples), and `POST /api/claims` answered `201` without a `Location` header.
4. No coverage measurement.
5. The GitHub repository starts with one commit holding the whole project (see F4 and Part F).

**Result**

* `SecurityConfig` rewritten without the dead code; the H2 console is **opt-in** (`H2_CONSOLE_ENABLED`, off by default in the properties and in
  the compose file), and its security rule and frame setting are added only when it is on.
* Controllers and DTOs documented for OpenAPI (tags, a summary and error responses on all 19 endpoints, examples on request bodies);
  `POST /api/claims` returns `201` with a `Location` header.
* JaCoCo 0.8.15 added; `mvn test` writes the report.
* `ApiContractTest`: the `Location` header, the console closed by default, and every endpoint documented (it fails if an endpoint has no summary).
* `ARCHITECTURE.md`: a "decisions and alternatives considered" table (A.1), API conventions (B.7), coverage in the testing section; README
  numbers and navigation links, `DOCKER.md`, the Swagger guide and the `CLAUDE.md` files brought in line.
* This journal: the top-moments summary, who did what, proposed decisions, F6 and F7.

**Corrections**

1. The AI's first annotations said an officer acting on a claim assigned to someone else gets `404`; the code returns `403`. Fixed before it
   was kept.
2. One DTO description said amounts are read in the market's currency; nothing in the code says that. Removed.
3. Two README navigation links pointed at headings that no longer existed. Fixed.

**Verification**

* **85 tests pass** (82 existing plus 3 new) on JDK 21, in a scratch copy. JaCoCo: **97.0% of lines (520/536), 96.7% of instructions, 85.7%
  of branches (90/105)**.
* **The real packaged application, on a spare port:** the H2 console answered `401` when off and `200` when on; creating a claim answered
  `201` with `Location: .../api/claims/1`; the OpenAPI document listed 19 operations under three tags.
* **Not verified:** Swagger UI looked at in a browser (only the JSON was read); the console page itself (only its status code); Docker.

**Decision:** `Pending author review`. *Proposed by the AI:* **ACCEPTED**: each change was verified by a test or on the running application
before it was kept.

## F8: Redesign the UI, and change the report form

**Prompt** (verbatim, spelling as sent)
```text
in frontend part the design is very simple and average also in Repost an incident screen Market should be dropdown with 6 market value like singapure , honkon , Indian etc also we can select past 1 week date only not more than 1 week incident we can select also policy number can auto generated please taken care this , please make the UI more attractive and proffesional now its very average and simple the login page also not looking good if possible add some logo as well
```

**Result** (frontend only; the backend and its API were not touched)

* **Design system** (`styles.css`): colour, radius and shadow tokens; light and dark themes that follow the operating system; a dark header with
  role navigation and a user chip; cards, forms, buttons, tables, badges, a claim progress bar, a history timeline, dashboard tiles with
  exposure bars; responsive down to a phone.
* **Logo and icons:** a shield-and-tick mark drawn for this project (not any company's logo), an inline SVG icon set, `public/logo.svg` as the
  favicon, page title "Claims Platform". No UI library, image or font is downloaded.
* **Sign-in and registration:** a split screen with a brand panel, and show/hide password.
* **Report an incident**, rebuilt in three sections:
  * **Market** is a dropdown of six: Singapore, Hong Kong, India, Malaysia, Thailand, Indonesia. The brief says Chubb has six markets but does
    not name them, so this list is the AI's assumption.
  * **Incident date** can only be from 7 days ago to today, with a hint and a clear message when it is outside. "Today" is the earlier of the viewer's date
    and the UTC date, so a claimant in Asia early in the morning is never offered a date that a UTC server (such as the Docker backend) would refuse as
    "in the future".
  * **Policy number** is generated (`POL-<year>-<six digits>`), read-only, with a button for a new one.
* **Claim page:** a progress bar (Submitted, Under review, Decision, Settled; a warning state for "more information", a red end for "rejected");
  claim and queue lists got icons, policy numbers and "waiting 3 h".
* **Tests: 216 to 250** (specs for the markets, dates, policy number and progress helpers; the report-form spec rewritten). The E2E guide (Parts 0
  and 3), README (with screenshots), ARCHITECTURE (C.1, C.7) and the `CLAUDE.md` files were updated.

**Corrections**

1. **The three form rules are UI-only.** The API still accepts any two-letter market, any past date and any policy number, so the 7-day limit can be
   bypassed by calling the API. This is stated in the README, in ARCHITECTURE (C.7 and part E) and in `frontend/CLAUDE.md`, not presented as a business rule
   the system guarantees.
2. **Found only by looking at the running app, not by the unit tests:** the logo tile was **blank on phone-width sign-in** (the brand panel is hidden
   there, and the visible logo reused the hidden one's gradient id); the car icon read as a headset; claim numbers wrapped in the phone queue; the phone
   header took four rows. All four were fixed and looked at again.
3. **An earlier statement of the AI was wrong again.** In F4 the AI said the demo-accounts buttons "only fill in the email". They fill in the email
   **and** the password (the code and its test show it) but do not sign in. Corrected in the README, the E2E guide and F4.
4. An early draft of a form hint told claimants to enter the amount "in the currency of the market". The API has no currency, so the hint was replaced
   by a factual one ("greater than zero, with at most two decimals").

**Verification**

* **250 tests pass** in 19 files; the strict production build is clean (initial 312.6 kB, 85 kB over the wire).
* **The running application** (backend jar plus the dev server), as claimant, officer and manager: the sign-in page; the report form (the dropdown's
  options and the date limits were read back from the page, an old date showed its message and kept "Submit claim" disabled, then a valid claim with
  a regenerated policy number was submitted and its page opened); the claims list; claim pages in the submitted, waiting-for-information and
  under-review states; the officer's claim page; the manager dashboard in light and dark mode; and the work queue and the sign-in and registration
  pages at phone width.
* **Not verified:** officer approve, reject and settle, and the manager's assign picker, were not clicked in this pass; no automated accessibility
  check (contrast, keyboard use) was run, only a look by eye; only a Chromium-based browser was used (the CSS uses `color-mix()`, which needs a current
  browser).

**Decision:** `Pending author review`. *Proposed by the AI:* **ACCEPTED**, with two things for the author to confirm: the six-market list (an
assumption) and whether the 7-day and generated-policy rules should also be enforced by the backend.

---

## F9: Add optional document upload, and explain every form error

**Prompt** (verbatim, spelling as sent)
```text
there is no document upload option in Report incident screen please add this option and make it optional and in UI screen if any required field missing then show a proper error desc
```

**This was a design question, not only a UI task.** The API had no way to receive a file, so a button on the form would have had nothing to send
to. The AI said so and asked how far to go; the author chose the full feature (backend and frontend) over a UI-only placeholder.

**Result**

* **Backend** (`backend/`): `POST /api/claims/{id}/documents` (one file per call, `multipart/form-data`), `GET .../documents` (details only) and
  `GET .../documents/{documentId}` (the file, as an attachment). New `ClaimDocument` entity and table, `ClaimDocumentService` (all the rules),
  `ClaimDocumentController`, `ClaimService.viewableClaim` (so a document is visible to exactly whoever can see the claim), three new error handlers.
  The API goes from 19 to 22 endpoints; `ApiContractTest` pins the new count and the "Documents" tag.
* **The rules the AI proposed** (the author to confirm): the claimant who owns the claim can attach, and only while it is not rejected or settled;
  officers and managers can read but not add; at most 5 files of 5 MB each; PDF, JPG or PNG, **decided from the file's first bytes** and the extension
  must agree (a name or a browser-reported type can be anything, so a script renamed `estimate.pdf` is refused); the name is stripped of directories;
  downloads are sent as an attachment with `nosniff` and `no-store`; files are stored as a BLOB in H2 (a stand-in for object storage).
* **Frontend** (`frontend/`): a fourth section on the report form, "Supporting documents (Optional)", with a drop box, a file list and a reason for
  every refused file; each missing or invalid required field now has its own message (`aria-invalid`, `aria-describedby`), an error summary lists
  them as links in page order, and focus moves to the first. The files are sent one by one *after* the claim is created, so a failed upload never
  undoes the claim: it is reported on the claim page, which also has a Documents box (download for everyone who can see the claim, "Add documents"
  for the owner while the claim is open). `canAddDocuments` lives in the action table; saving a download and the one-time message to the next page are
  two small services (`save-file.ts`, `flash.ts`); nginx's body limit was raised to 7 MB, because its 1 MB default would refuse uploads in Docker.
* **This reverses a behaviour from F8.** There, an invalid form left "Submit claim" disabled. It now stays clickable and explains what is wrong,
  because a disabled button says nothing about *what* is missing, which is what the author asked for.
* **Tests:** backend 85 to 117, frontend 250 to 312. **Documents updated:** README (one new screenshot, one replaced), ARCHITECTURE (A.1, B.2, B.5 to B.9, C.1 to C.3,
  C.5, C.7, C.8, E), all three `CLAUDE.md` files, `DOCKER.md`, the E2E guide (steps 3.1 to 3.3, 5.4, 6.5A, 7.9, Part 10) and the Swagger guide (steps 12A to 12D, 23A).

**Corrections**

1. **The AI broke its own spec with a PowerShell here-string.** It wrote `claim-actions.spec.ts` through a double-quoted here-string, which interpolated
   `${viewer.role} on ${status}` and broke the template literal; the test run caught it and the Edit tool fixed it. Code containing `$` or backticks is now
   written with single-quoted here-strings or Edit.
2. **A wrong download header, found by a live request, not by a test.** The first version sent an RFC 2047 encoded `=?UTF-8?Q?...?=` filename even for a
   plain name such as `estimate.pdf`. It now sends a plain `filename="..."` for simple ASCII names and the encoded form only for others, with an
   integration test for both.
3. **"Choose files" showed as bare text:** the button styles covered `button` and `a.btn` but not `label.btn`. Found by looking at the running form.
4. **The phone layout was cramped, and the first fix was incomplete.** Looking at the claim page at 375 px showed file names breaking mid-word
   (`damage-` / `photo.png`) and the details wrapping to four lines beside the Download button. The first CSS fix covered only the claim page's rows; the
   staged-files list on the form still wrapped a long name over five lines until a second look. Both re-checked at 375 px in dark mode.
5. **The AI wrote a wrong error message into a guide.** It first put "is larger than 5 MB" in the Swagger guide for an oversized upload. Reading the
   exception handler before leaving the line showed that, with the default settings, the web server stops the request earlier and the text is "The uploaded
   file is larger than the size limit". Corrected. (The same habit applied to the E2E guide: its "Submit claim is disabled" and old messages were rewritten from
   the strings in the code, not from memory.)
6. **A client artefact, not a defect:** posting an over-size file from .NET's `HttpClient` reported a stream error, while `curl` received the clean `413`
   problem document. It was treated as the client's behaviour; the form checks the size before sending, so a claimant does not meet it.

**Verification**

* **Backend:** 117 tests, 0 failures, re-run on 2026-10-26 in a scratch copy (so the running app's build output was not touched); JaCoCo 97.1% of lines (639/658),
  86.9% of branches (133/153). Live requests to the running backend (PowerShell and curl) covered upload, refusal, download and the access rules; the integration
  tests cover the same cases.
* **Frontend:** 312 tests in 20 files; the strict production build is clean (315.80 kB initial, 85.90 kB over the wire).
* **The running application** (backend jar plus dev server): an empty submit showed four field messages and a summary of four links with focus on the first; a valid claim
  with a PDF and a PNG chosen (a `.txt` and a 6 MB file listed as refused, each with its reason) was submitted, and its page showed "Claim submitted with 2 documents." and
  the Documents box; Download gave the right names, sizes, types and first bytes (the click was intercepted so no file was written); "Add documents" attached a JPEG and
  refused a PNG named `.pdf` with the server's message; the officer saw the documents and no "Add documents"; the report form's documents section and the claim page were looked
  at in dark mode at phone width.
* **Not verified:** the real file-chooser dialog and drag-and-drop (files were handed to the form by a script, because the built-in browser has no chooser); a closed
  (rejected or settled) claim and the five-file limit in a browser (unit and integration tests only); a file over 5 MB through a browser; the nginx 7 MB limit
  (Docker has never been run here); a download saved to disk. The Postman collection was not extended. The new E2E steps were checked piece by piece, not replayed as
  one flow. There is no virus scan, and the files are lost on restart.

**Decision:** `Pending author review`. *Proposed by the AI:* **ACCEPTED**, with these for the author to confirm: the limits (5 files, 5 MB, PDF, JPG, PNG); that officers and
managers can read but not add documents; that there is no delete or replace; and the BLOB-in-H2 storage as a stand-in for object storage.

---

## F10: A notification service that consumes the Kafka events and emails the claimant

**Prompt** (verbatim, spelling as sent)
```text
so now i need the kafka implemitation like when any status changes appear so it will send an event and you need to add one notification service where kafka consumer consume the topic data and send email to the customer or claimates
```

**What the AI found before building anything.** The producing half already existed (the outbox, the relay and `KafkaEventPublisher`, from D1), so "send an event when a
status changes" needed no new code. What was missing was the consumer, and, less obviously, **the event did not say who to email**: it had no claimant address. The AI
reported both, proposed a separate service (B.1 had already named notifications as the first thing to split out), and asked before downloading anything, because the mail
libraries were not in the local Maven cache. The author allowed the download.

**Result**

* **Backend:** `ClaimStatusChanged` gains `claimantEmail` and `claimantName` (from the claim, not from the officer who made the change). Fields are only added, so older consumers
  keep working; a new test fails if any contract field disappears. Backend tests 117 to 118.
* **`notification-service/`** (new, Spring Boot 3.4.5, no database): `@KafkaListener` on `claims.claim-status-changed`, an `EventParser` that rejects what cannot be emailed,
  `NotificationService` (one event in, at most one email out; a repeat of an event id is skipped), `EmailComposer` (plain-text and HTML parts, wording per status, a link to
  the claim page, the amount shown with its market because claims have no currency, the officer's notes deliberately left out), `SmtpEmailSender` (and a log-only mode for
  running without a mail server), and an error handler: a failed email is retried after 1, 2 and 4 seconds and then copied to a dead-letter topic, while a record that can never
  work (broken JSON, no usable address) is dead-lettered at once. Offsets are committed only after the email was handed to the mail server. It runs on port 8081 with only a health endpoint.
* **Containers:** `notification-service/Dockerfile`; `docker-compose.yml` gains `notification-service` and `mailpit` (a local inbox at http://localhost:8025), and `kafka-init`
  now also creates the dead-letter topic.
* **Documents:** README (an "Email notifications" section), ARCHITECTURE (A, A.1, B.1, B.3, new B.10, E), DOCKER.md (ports, parts 4 to 5, troubleshooting), a new
  `notification-service/CLAUDE.md`, the root and backend `CLAUDE.md` (the event is a contract and holds personal data), the E2E guide (new optional Part 13) and the Swagger guide.

**Corrections and findings**

1. **The tests were green and the service was still wrong.** The first live run of the real applications showed the consumer group was `claim-status-notifications`, not the configured
   `claims-notification-service`: in Spring Kafka a listener's `id` is also its group id unless `idIsGroup = false`, so the `NOTIFICATIONS_GROUP_ID` setting was silently ignored. The group
   decides where the service resumes after a restart, so this is a real defect. Fixed, then a test that asserts the configured group was written and **seen to fail with the fix reverted**
   (`expected: <notification-it> but was: <claim-status-notifications>`) before the fix was restored.
2. **A log line would have leaked part of the payload.** Reading the test output showed the retry logger printing Jackson's root-cause message, which can quote the start of the input, and
   the input holds an email address. The logger now stops at the service's own `BadEventException`, with a test whose premise (that Jackson does quote the payload) is itself asserted.
3. **The AI's own test slips.** A first draft of `NotificationServiceTest` contained a placeholder test that asserted nothing (and would have failed Mockito's strict-stubbing check), and
   another test had a pointless variable. Both were removed rather than left to pad the count. The coverage report then showed the documented `log` mode had no test at all; two were added,
   one of them pinning that the address is masked in that mode too.
4. **A wrong assumption about the test broker.** The AI asked the in-JVM Kafka broker to listen on port 9094; it ignored that and used a random port (61179). The live run used the port it got.
5. **The AI went beyond what was approved.** It was allowed to download the mail starter and GreenMail. Running `mvn dependency:resolve` also pulled in the Maven dependency plugin and its
   build tooling (Doxia, Velocity and similar), which it had not described. They are build tools, not part of the application, but they were more than the author agreed to and should have been mentioned first.
6. **Slow test runs, one cause found and one not proven.** One full run took about ten minutes instead of about a minute (a Kafka `CommitFailedException` and an SMTP read timeout in its log), and
   a later one had a single test taking 61 seconds. Per-test timings showed the time went into the test helper, which joined a new Kafka consumer group in every test to read the dead-letter topic.
   It now assigns the partitions directly, with no group: that test dropped to 5 seconds and the whole Kafka suite ran in 67 seconds. That removes the most likely cause, but the ten-minute run was
   seen once and was not reproduced, so it is not proven to be the same thing (a stalled machine remains possible).

**Verification**

* **Backend:** 118 tests, 0 failures, in a scratch copy (JaCoCo 97.1% of lines, 86.9% of branches, unchanged). **Notification service:** 76 tests, 0 failures; 99.0% of lines (200/202, only `main()` is not run), 88.6% of branches. They
  include parser tests with hostile input, every status's wording, HTML escaping, header-injection attempts, the SMTP sender against a real SMTP server (GreenMail) with non-ASCII names and a dead
  server, and two Spring Boot tests against **a real Kafka broker started in the JVM**: an event on the topic becomes an email; a claim's emails keep their order; a repeated event is emailed once;
  a poison record and a record with no address are dead-lettered without blocking the next one; a temporary mail failure is retried until it works; a mail server that stays down sends the record
  to the dead-letter topic after the retries.
* **The real applications together, once, outside Docker (2026-10-26):** the new backend jar publishing to a real Kafka broker, the notification service jar consuming, and an SMTP server receiving. A
  claim registered, reported, picked up, reviewed, questioned, answered, approved and settled through the API produced **six emails, in order, to the claimant** (not the officer), with the right
  subjects, a text and an HTML part, the claim link and the amount with its market. The service's log showed only `m***@example.com`. After a restart of the service the mailbox still held six emails:
  nothing was sent again.
* **Not verified:** the Docker image and the new compose services (the compose file was only parsed with SnakeYAML, never run by Docker, and the `axllent/mailpit:latest` image was never pulled); Mailpit; a
  real SMTP provider (TLS, authentication, rate limits); a mail-server outage and the dead-letter topic in the live run (they are covered by the tests only); more than one consumer thread; events published by a
  backend older than this change (they have no address and would be dead-lettered); the new E2E Part 13 (written from the same rules, its C2 and C3 counts not observed). The frontend was not changed.

**Decision:** `Pending author review`. *Proposed by the AI:* **ACCEPTED**, with these for the author to confirm: a separate service rather than a consumer inside the backend; the claimant's email and name travelling
on the topic (personal data on the topic and on the dead-letter topic, see ARCHITECTURE section 12); duplicate protection kept in memory only; English-only, email-only wording; and whether the extra build tooling
downloaded in correction 5 is acceptable.

---

## F11: Check the whole project after the Kafka work, and fix the documents

**Prompt** (verbatim, spelling as sent)
```text
now in our project we implemented kafka as well please verify the project once and also verify the README, ARCHITECTURE and AI_JOURNAL file is anything need to add and dont change the file diagram style if anything need to add please add and make the files simple wording to understand easyly
```

**What was found**

1. **The README was an older copy.** The author's last commit ("add Minor modification") rewrote `ARCHITECTURE.md` and put back a `README.md` that still said 85 backend tests and 216 frontend tests, "Kafka has not been verified against a real broker", and had nothing about the
   notification service, Mailpit, claim documents or the screenshots (the screenshot files were committed, but nothing showed them).
2. **`ARCHITECTURE.md` pointed to three pictures that did not exist.** It shows `docs/diagrams/system-architecture.svg`, `transactional-outbox.svg` and `claim-lifecycle.svg`, but there was no `docs/diagrams` folder on the machine or in git history, so GitHub would show three broken images.
3. **Old section letters were everywhere.** The rewritten `ARCHITECTURE.md` uses numbered sections 1 to 12, but the README, `DOCKER.md`, the four `CLAUDE.md` files and the journal still said "part E", "B.3", "B.10" and so on.
4. **The notification service's default mode had changed.** The author's `application.properties` now defaults to `log` (print the email), and my earlier notes said the default was `smtp`. Docker Compose still sets `smtp`.
5. **`ARCHITECTURE.md` was missing a few things it needs:** the event shape and the retry/dead-letter behaviour of the notification service in plain words, the "no currency" rule, why the UI keeps its own copy of the "who can do what" table, and several honest limitations (memory-only duplicate protection, nobody watching the dead-letter topic, email addresses on the topic).

**What was done** (the diagram style was kept: Mermaid diagrams stay Mermaid, the image diagrams stay images)

* **Re-checked everything from the committed state:** notification service, backend (in a scratch copy) and frontend, with fresh numbers (below).
* **README:** brought up to date and kept the author's structure and both Mermaid diagrams. Only three nodes were added to the first diagram (notification service, mail inbox, dead-letter topic). Added: the screenshots, a new "Email notifications" section, the documents endpoints and rules, the form rules, current counts and the corrected Kafka/Docker status.
* **`ARCHITECTURE.md`:** kept the author's layout and wording style. Added: fuller sections 4.3, 7.3 and 7.4 (with one Mermaid flowchart in the same colours as the existing one), new 10.5 (no currency) and 10.6 (UI rules table), testing results and the live run in 11, more rows in 12, and two more local addresses in Appendix A. The opening note was made simpler.
* **The three diagrams:** created as SVG files at the exact paths the document already uses, in the same colour palette as the Mermaid diagram (people blue, frontend green, backend purple, storage amber, events pink). Each was opened in a browser; overlaps were found and fixed (a heading crossed by an arrow, two labels touching arrows). **If the author has their own versions, copying them over these three files is all that is needed.**
* **Other documents:** every old section reference was replaced with the new number; `notification-service/CLAUDE.md` and `DOCKER.md` now say the default mode is `log` and that `EVENTS_PUBLISHER=log` means no emails; the journal got a short map from old section letters to new numbers and simpler opening text.

**Corrections to my own work**

1. My first version of the notification diagram in `ARCHITECTURE.md` used text arrows, which is a different style from the Mermaid diagrams in that file. I noticed before moving on and redrew it as Mermaid.
2. My earlier documents had said the notification service sends by default. That was true when I wrote it and stopped being true when the author changed the default, so the claim was out of date. It was caught by reading the current `application.properties` instead of trusting my notes.

**Verification (2026-10-26, from the committed state)**

* **Notification service:** 76 tests, 0 failures; 99.0% of lines (200/202; only `main()` is not run), 88.6% of branches (78/88).
* **Backend** (scratch copy, so the running app was untouched): 118 tests, 0 failures; 97.1% of lines (639/658), 86.9% of branches (133/153).
* **Frontend:** 312 tests in 20 files, 0 failures; the production build is clean (315.83 kB first load).
* **Documents:** 65 relative links and images across 8 documents were checked and all point to files that exist (one apparent anchor failure was my checker mishandling a dash, not a real problem). Each of the three SVG files is valid XML and was looked at in a browser. The image links from the README all resolve.
* **Not verified:** the Mermaid diagrams I added or changed were not drawn here (no Mermaid renderer is available offline), so they follow the syntax of the existing ones but should be glanced at on GitHub. Docker Compose and the two new containers still have not been run. Nothing about a real mail provider has changed.

**Decision:** `Pending author review`. *Proposed by the AI:* **ACCEPTED**, with these for the author to confirm: that the three SVGs I drew are acceptable stand-ins (or replace them with your own), that the screenshots belong back in the README, and that the section "10.6" and the extra limitations belong in `ARCHITECTURE.md`.

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
| Messaging | The Kafka publisher is unit-tested with a mocked template, and publisher and consumer ran against a real broker started inside a JVM (F10); no result of running them against the Docker stack's broker is recorded. The notification service's duplicate protection is in memory, claimants' email addresses travel on the topic and the dead-letter topic, and nothing watches or replays the dead-letter topic. |
| Database | In-memory H2 with `ddl-auto=update` and no migration tooling: **all data is lost on restart**. A PostgreSQL example is only a comment in `application.properties` (no driver is a dependency). The H2 console is opt-in (`H2_CONSOLE_ENABLED`, off by default); when on it needs no sign-in (local development only). |
| Secrets | Default JWT secret and seeded-account password are for local use only. |
| Documents | Uploaded files are stored as BLOBs in the in-memory database (lost on restart, read fully into memory), are not virus-scanned, are typed by their first bytes only, and cannot be removed or replaced. Limits: 5 files of 5 MB per claim. |
| Quality metrics | JaCoCo reports 97.1% line and 86.9% branch coverage (2026-10-26). It is a report, not a build gate, and there is no CI. |

Frontend and integration (full table, with a next step for each, in `ARCHITECTURE.md` section 12): no CORS on the backend, so the UI needs the dev
proxy or a same-origin gateway; the action table is a copy of backend rules; no currency on claims; the JWT is kept in `localStorage`;
officer approve/reject/settle and the manager assign picker were not clicked through in a browser (dark mode and phone width were looked at by eye on 2026-10-26); the
real file-chooser dialog and drag-and-drop were not exercised; no browser-level automated tests.

---

# Part F: Open Items

These are the author's to write; the AI has not filled them in because it would have to invent them.

1. **Pre-existing commits.** The backend repository already had commits before the submission work (`Initial commit` to `pagination
   added`). State which parts were AI-generated, which were written by hand, and how they relate to the Phase 1 build.
2. **F1 prompt text.** If an exact transcript is available, replace the clearly labelled reconstruction with it; otherwise keep the authenticity note.
3. **Decisions for F1 to F11** are all `Pending author review`, each with a verdict *proposed by the AI*. Confirm or change every one in your
   own words (one or two sentences: what you accepted, what you challenged or overrode, and why). **This is the part of the journal only you
   can write, and the part the brief asks for.**
4. **F3 was reconstructed.** Read it and correct anything that does not match what you remember of that session.
5. **Repository history.** The GitHub repository `chubb-claims-project` starts with one commit holding all 147 files; the development
   history (28 commits, including the backend's) exists only in `chubb-claims-platform`. The brief asks for a meaningful commit history
   showing the process. Decide: submit the platform repository's history together with the newer changes, or keep this repository and
   explain the squash in the walkthrough. Either way, commit in small, descriptive steps from now on (the recent messages are "minor
   modification", "changes added", "file modified").
6. **Backend commit hashes** (platform repository). Its backend history has the same commits, messages, authors and dates as the original,
   but different hashes, because `.build-cache/` was removed from every commit (see F4).
7. **Docker and Kafka run (F5).** Follow `DOCKER.md`, and write what happened, including any fix needed, into F5. Decide whether to add
   `spring.kafka.producer.properties.max.block.ms=10000` to the backend.
8. **F6 reason.** Write why you removed PostgreSQL ("some reason" is what was recorded); the panel is likely to ask why H2.
9. **Fresh-clone check.** Clone the repository somewhere clean, run `npm install` in `frontend/`, and follow `README.md` once.
10. **The report form's assumptions (F8).** Confirm the six markets (the brief names none), and decide whether the 7-day limit and the generated
    policy number should also be enforced by the backend. Also run the E2E guide once in a browser: its Parts 0 and 3 were rewritten for the new form
    but the whole flow was not re-run.
11. **Documents (F9).** Confirm the limits (5 files, 5 MB, PDF/JPG/PNG), that officers and managers can only read documents, and that there is no delete.
    Try the upload with real files through the browser's own file chooser and by drag-and-drop (the AI could only hand files to the form by script), and
    follow E2E steps 3.2A, 6.5A and 7.9 once. Decide whether the Postman collection should get the three new requests, and what you would say about
    storing files in the database (the answer in `ARCHITECTURE.md` sections 4.3 and 12: fine for an in-memory demo, object storage for real use).
12. **Notifications (F10).** Run the Docker stack (`DOCKER.md` part 5.3A and 5.3B), look at the emails in Mailpit at http://localhost:8025, and write down what happened:
    the compose file and the two new containers have never been run. Try one real SMTP account through the `MAIL_*` variables. Confirm the decisions F10 lists (separate
    service; the claimant's address on the topic; in-memory duplicate protection), and decide whether the extra Maven build tooling that was downloaded is acceptable.
    Then follow E2E Part 13 once.
13. **Final check (F11).** Look at the three diagrams in `docs/diagrams/` and at the Mermaid diagrams in the README and `ARCHITECTURE.md` on GitHub (they were not
    drawn in this session). Replace the three SVGs with your own if you have them. Check the README still reads the way you want, since your last commit had replaced it.

---

# Appendix: Template for New Entries

```markdown
## F12: <short title>

**Prompt** (verbatim, or "reconstructed prompt summary (not verbatim)"): <what was typed>

**Result:** what the AI produced or changed; list files.

**Corrections:** anything the AI got wrong, or the author challenged, and how it was resolved, or "none".

**Verification:** what was actually run or checked (build, tests, manual) and what was NOT.

**Decision:** ACCEPTED | CHALLENGED | OVERRODE, with the author's reasoning, or `Pending author review` plus a *Proposed* verdict.
```

Only add an entry when the prompt set the design, found or fixed a defect, changed the repository, or the AI was corrected.
