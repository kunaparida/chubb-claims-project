# AI Working Journal

| | |
|---|---|
| **Project** | Claims Platform: `backend/` (Spring Boot, formerly `chubb-claims-backendService`) and `frontend/` (Angular, formerly `claim_management_UI`) |
| **Author** | Kuna Parida |
| **Document purpose** | Transparent record of how AI assistance was used: what was requested, what was produced, and what was accepted, challenged or overridden, with the reasoning. |
| **Last updated** | 2026-10-23 (backend entries 2026-10-16; frontend entries 2026-10-22; consolidation 2026-10-23) |

This is the single journal for the whole submission. It was formed by merging the backend journal (Parts A, B, C and D of the
original, unchanged apart from lettering) with the frontend journal (Part C below, plus its open questions in Part D).

The author reviews and takes responsibility for every line in this repository. AI output was treated as a proposal, not
as a result, until it was verified by a build, a test run or a manual check. Each entry says what was **actually verified**
and what was **not**.

> **How to read the Decision line.** `Decision` is the *author's* call. Where the author has not yet reviewed an entry it says
> `Pending author review`; it is deliberately not pre-filled. Facts the AI found or got wrong are recorded under *Result* and
> *Corrections*, not disguised as the author's decisions. The backend entries (Phases 1 to 3) keep the decisions recorded in the
> original backend journal; the frontend entries F1 to F6 are still pending.

## 1. Conventions

| Decision | Meaning |
|---|---|
| **ACCEPTED** | AI output used as generated. |
| **CHALLENGED** | AI output questioned or verified, then kept or changed. |
| **OVERRODE** | The author decided differently from the AI. |

Prompts appear in the order they were sent. Backend entries (Part A) follow the structure *Prompt, Result, Decision, Verification*;
frontend entries (Part C) add a *Corrections* section and put *Decision* last. Frontend prompts are numbered **F1 to F6** so they
do not collide with the backend's Prompts 1 to 23.

## 2. Summary of the work

| Phase | Prompts | Location | Purpose |
|---|---|---|---|
| 1. Reference build | 1-11 | `C:\project\claims-management` | First complete Spring Boot claims system, built from an architect-style prompt, used to learn the domain and obtain a working, tested baseline. |
| 2. Presentation material | 12-13 | `claims-management/docs` | Walkthrough guide, first journal draft and prompt list. |
| 3. Submission repository | 14-23 | `C:\project\chubb-claims-backendService` (now `backend/`) | The backend under review. Verified against the assessment brief and extended to close gaps. |
| 4. Frontend integration | F1-F3 | `C:\project\claim_management_UI` (now `frontend/`) | `CLAUDE.md` and journal first, then all 19 endpoints integrated into one Angular app for three roles, then a manual end-to-end script. |
| 5. Submission packaging | F4-F5 | repository root | One repository with one README, one ARCHITECTURE and this one journal. |
| 6. Containerisation | F6 | repository root, `backend/`, `frontend/` | Dockerfiles, a compose file with Kafka, a Kafka configuration check, and `DOCKER.md`. |

## 3. Key outcomes

- A layered, modular-monolith Spring Boot 3.4 / Java 21 application with JWT security, role-based access and RFC 7807 errors.
- Workflow rules enforced in the service layer; invalid transitions raise `BusinessException`.
- Audit history and a transactional outbox for status-change events (log publisher by default, Kafka optional).
- `mvn clean verify` passes on JDK 27 with 82 tests and 0 failures (42 before Phase 3 work).
- End-to-end replay of the Postman flow against a running instance returned the expected status codes (200/201/400/403/409).
- An Angular 22 UI (zoneless, signals) integrates all 19 endpoints for the three roles in one app: 216 Vitest tests, a strict
  production build (about 293 kB initial), a scripted lifecycle run against the real backend, and a browser walkthrough.
  What was **not** clicked through in a browser is listed in F2 and in `ARCHITECTURE.md` part E.
- Both applications, their histories and one set of documents are in a single repository (F5).

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

**Decision: ACCEPTED** as a baseline only. The AI had no Maven in its environment, so nothing was compiled until Prompt 5.

**Verification:** deferred to Prompts 2 and 5.

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

### Prompt 3: Move the project and switch to H2

**Prompt**
```text
I moved the repo to C:\project; use this location from now on.
I don't have a database installed, so make H2 the default database.
Keep the PostgreSQL configuration, but commented out.
```

**Result:** `application.yml` uses in-memory H2 by default with the PostgreSQL block commented out. The H2 profile file
was removed and the README updated.

**Decision: OVERRODE** the AI's default (PostgreSQL) because of the author's environment constraint (no local database).

**Verification:** application starts with no external database.

### Prompt 4: Compile error

**Prompt**
```text
java: java.lang.ExceptionInInitializerError
com.sun.tools.javac.code.TypeTag :: UNKNOWN
while running
```

**Result:** First fix was a Lombok upgrade in `pom.xml`.

**Decision: CHALLENGED.** The Lombok upgrade alone did not resolve the error on the author's machine.

**Verification:** see *Open items* in Part F (final resolution is not recorded here).

### Prompt 5: Maven build error in the submission repository

**Prompt**
```text
(pasted) [ERROR] Failed to execute goal ...maven-surefire-plugin:3.5.3:test ... on project claims-management
See C:\project\chubb-claims-backendService\target\surefire-reports for the individual test results.
Getting this error while trying maven clean and install.
```

**Result:** The AI read `target/surefire-reports`. Seven tests failed with "Mockito cannot mock this class": the Byte
Buddy version managed by Spring Boot 3.4.5 does not support JDK 27. It added `byte-buddy.version=1.18.14` to `pom.xml`.

**Decision: ACCEPTED**, because the cause was reproduced and the fix confirmed on the same JDK 27 that failed.

**Verification:** `mvn clean install` passed (42 tests).

---

# Part B: Backend Design Decisions (Phase 3)

## B1. Build failure after `mvn clean install`

- **Context:** see Prompt 5.
- **Fix:** pinned `byte-buddy.version`.
- **Verified by:** re-running on the failing JDK 27, result `BUILD SUCCESS`.

## B6. Audit history and events (transactional outbox)

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
- **Known limit:** Kafka is wired and unit-tested with a mocked template, but has **not** been run against a real broker.

## B7. Claim type and market

- **AI proposal:** `claimType` (MOTOR, PROPERTY) and `market` as a 2-letter code; liability broken down by both.
- **Decision: OVERRODE** a `Market` enum. The set of markets is not known, so an enum would invent business data.
- **Breaking change:** both fields are required on submission. The Postman collection and all existing tests were
  updated in the same change.

## B8. Architecture: modular monolith with REST plus outbox

- One bounded context, one transaction per claim change, within a 2-3 hour budget.
- Split candidates for the future: notifications and reporting, connected by the `ClaimStatusChanged` event.
- Full reasoning is recorded in `ARCHITECTURE.md` (part B).

## B9. Verification before trust

- **AI mistake caught:** while writing the new service tests, the AI first saved an unfinished placeholder test file and
  then rewrote it. Only a green build is counted as evidence.
- **Checks run:**
  - `mvn clean verify` on JDK 27: 82 tests, 0 failures (previously 42).
  - End-to-end replay of the Postman flow against a freshly started app: every step returned the expected status
    (200/201/400/403/409); history showed all six transitions; outbox events appeared in the log.
  - Context tests prove the publisher is `LoggingEventPublisher` by default and `KafkaEventPublisher` when switched.

---

# Part C: Frontend Prompt Log (Phase 4 and 5)

These entries were written in the frontend repository before the two repositories were combined. What changed when they were
carried over (F5 explains why):

* Numbered **F1 to F6** instead of Prompt 1 to 3, so they do not collide with the backend prompts.
* Absolute paths inside the entries are the **original** ones and are left as history: `C:\project\claim_management_UI` is now
  `frontend/`, and `C:\project\chubb-claims-backendService` is now `backend/`.
* **F1:** the closing code fence after the prompt text was missing in the committed journal, so the rest of the entry rendered as
  code. The fence was closed; no word of the prompt text was changed, and a note about it follows the prompt (see Part F).
* **F3** was missing from the committed journal even though the status table pointed to it. It was **reconstructed by the AI on
  2026-10-23** from that session, using the facts the tests and the guide itself still prove (see the entry).
* The status table is updated: the frontend is now committed (as part of this repository).

## Frontend context

| Item | Where | Notes |
|---|---|---|
| **This UI** | `frontend/` (was `C:\project\claim_management_UI`) | Angular **22.2**, zoneless, Vitest. Created with the CLI; untouched scaffold at the start of F1. |
| **Backend (the one this UI integrates)** | `backend/` (was `C:\project\chubb-claims-backendService`) | Spring Boot 3.4.5 / Java 21. Swagger: `http://localhost:8080/swagger-ui/index.html`. |
| **Assessment brief** | `V2_FullStack_candidate_assessment_brief.pdf` (Chubb APAC) | Backend first; "a well-built backend with a partial frontend will outscore a rushed attempt at both". The UI serves claimants, claims staff and managers from one Angular app. |

## Frontend status

| Area | State |
|---|---|
| `frontend/CLAUDE.md` | Written (F1), updated with the real state (F2). |
| Dev proxy / CORS workaround | Done and proven (see F2) |
| Models, `ApiService`, auth, interceptors, guards | Done: 19/19 backend endpoints covered |
| Claimant screens | Done: register, login, my claims, report incident, claim page (answer questions) |
| Officer screens | Done: work queue, pick up, assigned claims, review / request info / approve / reject / settle |
| Manager dashboard | Done: workload, liability per market, officer performance, assign picker |
| Tests (Vitest) | 216 passing in 15 files |
| Manual end-to-end script | `docs/E2E-VALIDATION-FLOW.txt` (F3) |
| Committed to git | Yes. F1 to F3 were written while nothing was committed; the author's commit `070bdc7` is imported unchanged under `frontend/` |

## F1: Create CLAUDE.md and this journal before integrating

**Prompt** (verbatim)
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

> **Consolidation note (AI, 2026-10-23), for the author to resolve.** The block above is the author's edited text, and it was left
> untouched. It is labelled "verbatim", but it is not the message the AI received in that session. That message gave the same two
> locations and the Swagger link, said the UI project was created but nothing integrated yet, and asked for a `CLAUDE.md` and an
> AI journal to be written first. Either replace the block with the original message, or change the label to "cleaned-up prompt".

**Result**

* Created `CLAUDE.md`: purpose, tooling, the full backend contract (19 endpoints, request/response shapes, error format), the
  status workflow, the backend facts that shape the UI, a UI action table, a brief→UI checklist, and architecture rules.
* Created the UI's `AI_JOURNAL.md` in the same structure as the backend's journal.
* **No code was written, nothing was integrated, nothing was committed, and the backend repository was not modified.**

**What the AI read before writing** (so the files describe the real projects, not assumptions)

* Backend: `AuthController`, `ClaimController`, `ManagerDashboardController`, all 14 DTOs, `SecurityConfig`,
  `GlobalExceptionHandler`, `ClaimService` (the rules), `ManagerDashboardService`, `application.properties`, its `README.md` and `CLAUDE.md`.
* UI: `package.json`, `angular.json`, `tsconfig.json`, `.prettierrc`, `app.config.ts`, `app.routes.ts`, `app.ts`, the default README.

**Findings that shape the integration** (each is also in `frontend/CLAUDE.md`)

1. **The backend has no CORS configuration** (searched `src/main` and `pom.xml`: nothing). A browser app on `:4200` calling `:8080`
   will be blocked. The UI therefore needs the Angular dev **proxy** and relative `/api` URLs. *Not* fixed by touching the backend.
2. **No `allowedActions`.** The backend never says which buttons to show, so the UI needs one central action table
   (status × role × assignment) mirroring `ClaimService`. The server stays the authority: 403/404/409 must be handled.
3. **`pickup`/`assign` do not change status** (`Claim.assign` only sets the officer). The claim stays `SUBMITTED` until the officer calls `review`.
4. **No "list officers" endpoint.** `GET /api/dashboard/performance` returns *all* officers (verified in `ManagerDashboardService`),
   so it can feed the manager's assign picker.
5. **Money has no currency.** `amountClaimed` is a bare number, `market` a 2-letter code, and the dashboard's top-level totals sum
   across markets (mixed currencies). The UI must show `byMarket` lines first and caveat the grand total.
6. IDs are numeric (`Long`); claim list endpoints are **role-scoped** (officer's `GET /api/claims` = assigned only; the queue is a separate endpoint).

**Corrections** (things the AI got wrong or that were in the way, recorded honestly)

* **Wrong earlier claim:** the AI earlier told the author that Node.js was not installed. It *is* installed (v26.3.1 under
  `C:\Program Files\nodejs`); the AI's own shell simply did not have it on its PATH. No download is needed for this UI.
* **Port conflict:** `localhost:8080` and `localhost:4200` were occupied by servers the AI had started earlier for the unrelated
  `Claim_Management` build, so the Swagger link in the prompt would have shown *that* API instead of this backend (its tags are
  "Claims", whereas this backend's controller shows as `claim-controller`). The AI stopped only its own two processes; both ports are now free.
  Start the real backend with `mvn spring-boot:run` before opening Swagger.

**Verification**

* Done: read the sources listed above; searched the backend for CORS configuration (none); queried `:8080` and saw the *other* app's
  OpenAPI document; confirmed both ports free after stopping the AI's processes.
* **Not done:** the real backend has not been started in this session, so the contract in `CLAUDE.md` comes from reading its code,
  not from calling it. The UI has not been installed, built or tested. The Swagger link was not opened.

**Decision:** `Pending author review`

---

## F2: Proceed, integrate the backend APIs into the UI

**Prompt** (verbatim)
```text
please procced
```

**Context.** This answered the AI's proposal to start with the proxy, models and API service, then login/guards, then the three role
screens. The AI also asked whether to commit as it went; the answer did not cover that, so **nothing was committed** (the repo's
`CLAUDE.md` says "commit only when asked"). Before installing anything the AI asked, and the author approved: *"Yes, install and run"*
(`npm install` in this repo, and starting the backend with Maven).

**Result** (all under `src/app`; templates live in `.html` files next to the component)

* `core/`: `models.ts` (types mirroring the backend DTOs), `api.service.ts` (the only file with URLs), `auth.service.ts`,
  `auth.interceptor.ts`, `guards.ts`, `problem.ts`, `claim-actions.ts` (the one action table).
* `shell/` (role navigation), `features/login`, `register`, `claims` (list, new-claim, claim-detail), `queue`, `dashboard`, `shared/`.
* `proxy.conf.json` + `angular.json` `serve.options.proxyConfig`; global styles with light/dark tokens.

**Design decisions** (and why)

* **Dev proxy, not a backend change.** The backend has no CORS configuration. Proven both ways against the running backend: login through
  `:4200/api` works; a direct cross-origin preflight to `:8080` returns **401 with no CORS headers**, which a browser would block.
* **One action table** (`claim-actions.ts`) because the backend does not say which actions are allowed. It is tested against an independent
  copy of the backend's `TRANSITIONS` map (every status × role × assignment). The server stays the authority: every action handles
  403/404/409 by showing the server's `detail` and reloading the claim.
* **Money shows a market, never a currency symbol**, and the dashboard shows per-market liability first with the grand total labelled
  "unconverted, mixed markets" (the backend sums across markets with no currency).
* Zoneless Angular 22: all template state is signals; no zone-dependent code.

**Corrections: what went wrong, and how it was resolved**

1. **A real component bug, found by a unit test.** In the work queue, losing a pick-up race (409) set the error message and then reloaded the
   list, and the reload cleared the message, so the officer would never have been told *why* the pick-up failed. Fixed with
   `load(keepError)`; the test `losing the race shows the server reason…` now passes.
2. **First full test run: 4 failures, none in app code except #1.** Two specs called `TestBed.inject` before configuring the module; one
   flushed a request that `forkJoin` had already cancelled (`verify({ ignoreCancelled: true })` is the right check); one spec did not register
   the auth interceptor, so a 401 could not sign anyone out in the test (the real app registers it). All fixed in the specs.
3. `GET /api/auth/me` was defined in `ApiService` but unused by any screen. It is now used on app load to re-validate the stored session
   (a changed role updates the navigation; a 401 signs the user out).
4. The browser tab title was still the CLI default ("ClaimManagementUi"); changed to "Claims".
5. One assumption confirmed rather than guessed: `pickup` leaves the claim `SUBMITTED` (checked live: it only sets the officer; `review` starts the review).

**Verification**

* **Contract:** live `/v3/api-docs` vs `ApiService`: 19 endpoints in the backend, 19 called by the UI, 0 missing either way (same verbs and paths).
* **Live lifecycle against the real backend** (through the dev proxy, same endpoints and bodies as the UI): 28 checks passed: register,
  login ×3, 401 / 400 field errors / 403 email-mismatch, create, queue, pick up (stays SUBMITTED), review, request-info, empty-note 400,
  409 invalid transition, claimant answer, approve, settle, 409 on a settled claim, 6-entry history, another claimant gets 404, manager
  sees all officers, dashboards, officer gets 403 on the dashboard, manager assigns.
* **Browser walkthrough** (real backend, real UI): registered a claimant → reported an incident (form prefilled from the session) → signed in as the
  seeded officer → claim in the queue → picked up → started review → requested information → signed in as the claimant → saw "Action needed" →
  answered → history showed all four steps → manager dashboard showed per-market liability with the unconverted-total label.
* **Unit tests:** 212 passing at the end of this prompt (216 after F3's fix); strict AOT production build clean (292.6 kB initial).
* **Mutation check:** made the action table offer SETTLE on `UNDER_REVIEW` (the backend forbids it) → 6 tests failed with
  "SETTLE must be legal from UNDER_REVIEW"; restored.

**Not verified** (be upfront in the walkthrough)

* In a real browser: officer **approve / reject / settle**, the manager's **assign picker**, dark mode, and narrow/mobile layout. The first three
  are covered by unit tests and the scripted API run, not by clicking.
* Accessibility beyond labels/roles/focus styles was not audited.
* `GET /api/claims` has only a status filter on the backend, so the claims list has no search or officer filter.

**Side effects on this machine**

* The AI started the backend with `mvn spring-boot:run` from `C:\project\chubb-claims-backendService` (this writes build output under that repo's
  `target/`; **no backend source file was changed**). Test data exists only in its in-memory H2 database and disappears on restart:
  claimants `ui.check.<timestamp>@example.com`, `other.<timestamp>@example.com`, `demo.claimant.ui@example.com`, and claims #1–#3.
* `npm install` created `node_modules/` (git-ignored). The backend (port 8080) and the Angular dev server (port 4200) were **left running**.

**Decision:** `Pending author review`

---

## F3: Provide the whole end-to-end flow as a text file

> **Reconstructed entry.** The committed journal pointed to this entry but did not contain it. The AI rewrote it on 2026-10-23
> from its own record of the session, and checked what it could against files that still exist (`docs/E2E-VALIDATION-FLOW.txt`,
> `frontend/src/app/core/problem.ts` and its spec). The scripted-run figures and the live checks (45 checks, the 401 after a
> restart) cannot be re-checked from a file. Where the session's exact detail is no longer available, the entry says less rather
> than more.

**Prompt** (verbatim)
```text
okay now for validating the end to end flow please provide me the whole flow in text file so I can follow and check everything
```

**Result**

* `docs/E2E-VALIDATION-FLOW.txt` (originally `docs/` of the UI repository): a tick-box script in plain ASCII with a results sheet.
  Parts 0 to 12: set-up, smoke checks, account creation, claimants reporting incidents with form validation, the manager dashboard
  before any work (checkpoint A), officer pick-up and review, information request and answer, approve and settle, manager assigns
  and officer rejects (checkpoint F), final state, "who may see what" by typing addresses, resilience, and an optional cross-check
  against the backend through Swagger. It also has a "things that look odd but are not bugs" list and a quick-diagnosis table.
* Fixed test data (two claimants, three claims) so the dashboard numbers at checkpoints A to F and the final state are predictable.
* A small UI fix that came out of preparing it (see Corrections): `problem.ts` now recognises a gateway error.

**Corrections**

1. **A real UI defect, found while producing the script.** With the backend stopped, the Angular dev proxy answers `502 Bad Gateway`
   with an empty body, not a network error with status 0. The UI only treated status 0 as "cannot reach the server", so a stopped
   backend would have shown a generic failure. `core/problem.ts` now maps 502, 503 and 504 (`GATEWAY_ERRORS`) to "Cannot reach the
   server…", with tests in `problem.spec.ts`. The suite went from 212 to **216** tests.
2. **The guide had to describe what the backend really does**, not what the AI expected: settling replaces the approval note in the
   claim's "Decision" field (the earlier note is still in the history), and a token that survives a backend restart gets a 401 and
   the UI signs out. Both are written into the guide; the 401 behaviour was checked live.
3. **The guide's first draft was wrong about the Node version.** It said "Node 20 or newer"; Angular 22 requires 22.22.3+, 24.15+ or
   26+ (read from the installed `@angular/core` package). Fixed during consolidation (F5), together with the folder references.

**Verification**

* Before the guide was written, the AI ran the whole flow against the real backend on a fresh database through the dev proxy with a
  script: **45 checks passed**, and the numbers in the guide's EXPECT lines are the numbers from that run. Behaviours the script
  relied on were checked against the backend rather than assumed (an unknown email, a duplicate registration, which claim ids a
  fresh database hands out).
* **Not verified:** the AI did not click through every step of the guide in a browser; the numbers come from the scripted API
  run, not from reading them off the screens. Only one officer is seeded, so a
  race between two officers cannot be tried by hand (the guide says so; the automated tests cover it). The author should run the
  guide once and record the results sheet.

**Side effects on this machine:** the backend was restarted for the run (empty database afterwards); nothing was committed.

**Decision:** `Pending author review`

---

## F4: One README, one architecture and one journal, or one per repository?

**Prompt** (verbatim)
```text
now I have both backend (C:\project\chubb-claims-backendService) and frontend (C:\project\claim_management_UI) repo we need to submit this two so for that we go for single readme file or separate separate readme file, architecture and AI Journal file please clear this
```

**Result:** advice in chat only; no file was changed. The answer is not recorded here in detail. The author's next prompt (F5)
chose a single set of documents for the submission.

**Verification:** none needed; no code or file was touched.

**Decision:** `Pending author review`

---

## F5: Put both projects in one repository with one set of documents

**Prompt** (verbatim)
```text
now I need to submit both project in once so I need both backend and UI in one project and for both we need one README , ARCHITECTURE and AIJOURNAL file is this possible?
```

**Result**

* A **new** repository, `chubb-claims-platform`, with the backend under `backend/` and the frontend under `frontend/`. The two
  original repositories were not modified.
* Both histories were imported with `git subtree add`, so every original commit is kept under its folder.
* Documents merged: one root `README.md` (the two READMEs folded together), one `ARCHITECTURE.md` (the backend's document plus new
  frontend, integration and limitations parts), and this journal (the backend journal plus the frontend journal). The two test
  guides moved to `docs/`; `backend/CLAUDE.md` and `frontend/CLAUDE.md` were updated; a short root `CLAUDE.md` indexes them.

**Corrections**

1. **The backend repository had a Maven cache committed** (`.build-cache/`, 151 jars, 82 MB), which a plain clone could not even
   check out on Windows (paths too long). It was removed from **every** imported backend commit with `git filter-branch`, so the
   backend's **commit hashes differ from the original repository's**; the file contents of the final tree are identical apart from
   `.build-cache/`.
2. **A command chain stopped halfway.** An `&&` chain failed on the long paths, so only its read-only commands had run in the
   frontend repository. The import was redone with a no-checkout clone and sparse checkout, and the originals were checked to be
   untouched afterwards. Separately, two shell commands run in parallel shared one working directory and printed the wrong
   repository's data once; every later command used `git -C <path>`.
3. **Mistakes in the first drafts of the new documents, found by checking them against the source:** the README said Node 20+ (see F3);
   it said the login page signs in with one click (the "Local demo accounts" panel only fills in the email); and the architecture
   limitations said lists load one page at a time (they are paged, 10 per page, with Previous and Next). All fixed.

**Verification**

* Tree identity: the imported `frontend/` tree is identical (same git tree hash) to the original repository's HEAD, and `backend/` is
  identical to the original with `.build-cache/` removed.
* Documents were checked for stale absolute paths and for broken relative links before the final commit (see the commit history).
* **Built and tested from the combined repository** (2026-10-23): backend `mvn -o test` on JDK 21: **82 tests, 0 failures**, BUILD
  SUCCESS; frontend `npm test -- --no-watch`: **216 passed in 15 files**; `npm run build`: clean. The frontend run used the original
  repository's `node_modules` through a temporary directory link (removed afterwards), because installing was not approved.
* **Not verified:** a first-time `npm install` in the combined `frontend/`, and starting both applications from the combined
  repository and clicking through them (the code is identical to what was walked through before). Nothing was pushed to any remote.

**Decision:** `Pending author review`

---

## F6: Dockerize the project, check the Kafka configuration, write the Docker guide

**Prompts** (verbatim; the first is the observation that led to the second)
```text
there is no docker file is present in this project
```
```text
you dockerize it and check kafka configuration and also create an md file where you need to list out the steps to install all the things in docker and run all the commands also to make the application succesful up
```

**Result** (all in the author's working copy `C:\project\chubb-claims-project`; nothing committed)

* `backend/Dockerfile` (Maven build stage on JDK 21, then a JRE-only alpine image, non-root, tests skipped unless
  `--build-arg SKIP_TESTS=false`), `frontend/Dockerfile` (Node 24 build stage, then nginx), `frontend/nginx.conf` (serves the SPA,
  falls back to `index.html`, forwards `/api/` to the backend so the missing CORS configuration never matters), and a
  `.dockerignore` in each app folder.
* `docker-compose.yml`: single-node Kafka 3.9 in KRaft mode with an internal and a host listener, a one-shot `kafka-init` that creates
  the topic, the backend (publisher switched to Kafka by environment variables, health check on `/v3/api-docs`), and the frontend on
  port 4200. `.gitattributes` keeps these files LF on Windows.
* `DOCKER.md`: installing WSL 2 and Docker Desktop, checking the install, freeing ports, build and run, a step-by-step way to watch
  events arrive in Kafka (including stopping the broker to show the outbox working), everyday commands, a Kafka configuration table,
  troubleshooting, and other ways to run.
* README, root `CLAUDE.md` and the architecture limitations list point to it.
* **The backend source was not changed.**

**Kafka configuration check: what was found** (details and the Docker setup's answer to each are in `DOCKER.md` part 7)

1. `spring.kafka.bootstrap-servers` is commented out in `application.properties`, so the broker address has to be supplied from
   outside; compose does it with `SPRING_KAFKA_BOOTSTRAP_SERVERS`, with no code change.
2. The application never creates its topic, so `kafka-init` does, and broker auto-creation is switched off so a wrong topic name fails
   loudly.
3. Serialization works as configured: no producer settings are needed because Spring Boot's String defaults match
   `KafkaTemplate<String, String>`; the key is the claim id.
4. Only the claim creation and the status transitions write outbox rows; `pickup` and `assign` do not, which is why picking up a claim
   shows no event. Checked in `ClaimService`.
5. **A weakness, not fixed:** with the broker down, `send()` can wait for Kafka's default `max.block.ms` (60 s) before the code's own
   10 s acknowledgement timeout applies, holding the relay thread and its transaction. The suggested setting
   (`spring.kafka.producer.properties.max.block.ms=10000`) is in `DOCKER.md`; the author decides whether to add it.
6. Nothing consumes the events yet; the guide shows how to watch them with the console consumer.

**Corrections**

* While preparing the frontend image the AI tried to run a local production build only to learn the output folder; the author
  interrupted that command. The folder (`dist/claim-management-ui/browser`) was instead confirmed from the installed Angular
  builder's schema (default browser folder name `browser`) and the project name in `package.json`.
* The AI chose a Debian-based Node image (`node:24-slim`) rather than alpine because the lock file was generated on Windows and native
  build dependencies are less likely to be missing on glibc. This is a judgement, not a tested result.

**Verification**

* Done: the Kafka code and configuration were read in full (`application.properties`, `KafkaEventPublisher`, `OutboxPublisher`,
  `pom.xml`, `SchedulingConfig`); which operations write events was checked in `ClaimService`; the outbox table name was checked; the
  Angular output folder was checked as described above.
* **Not verified, and important:** Docker is **not installed** on this machine, so **no image was built, `docker compose config` was
  not run, and no container was started.** Not confirmed by running: that `node:24-slim` satisfies Angular 22's Node range at build
  time, the `apache/kafka:3.9.0` environment variables and health check, the backend health check command, and the nginx
  configuration. The Kafka publisher has still never run against a real broker. `DOCKER.md` part 5 is the procedure to settle all of
  this; **the author should run it and record the outcome here.**

**Decision:** `Pending author review`

---

# Part D: Open Questions for the Backend

The frontend does not change the backend. These are things the author may want to change there (or accept as limitations and explain in the walkthrough).

| # | Question | Impact on the UI |
|---|---|---|
| 1 | Add CORS (or accept a dev-proxy-only setup)? | **Confirmed needed:** a direct call from `:4200` gets a 401 with no CORS headers. The UI works only through the dev proxy or a same-origin deployment. |
| 2 | Expose an officers list (or accept `/dashboard/performance` as the source)? | Manager assign-picker. |
| 3 | Add a currency (or a market→currency mapping) to claims and the liability report? | Without it the UI cannot show correct money, and grand totals mix currencies. |
| 4 | Return allowed actions per claim? | Today the UI mirrors the workflow rules itself, which can drift from the backend. |
| 5 | Reassigning `REJECTED`/`SETTLED` claims is not forbidden (`backend/CLAUDE.md` notes this). | UI simply will not offer it. |

---

# Part E: Known Limitations

Backend:

| Area | Current state |
|---|---|
| Concurrency | No optimistic locking (`@Version`). |
| Messaging | No test against a real Kafka broker. |
| Database | `ddl-auto=update` and in-memory H2 by default; no migration tooling. |
| Secrets | Default JWT secret is for local use only. |
| Quality metrics | Code coverage is not measured in this repository (no JaCoCo plugin). |

Frontend and integration (the full table, with a next step for each, is in `ARCHITECTURE.md` part E):

| Area | Current state |
|---|---|
| CORS | The backend has none, so the UI works only through the dev proxy or a same-origin gateway. |
| Action table | The UI keeps its own copy of the backend's who-can-do-what rules; it can drift. |
| Currency | No currency on claims; amounts are shown by market and cross-market totals are labelled unconverted. |
| Browser coverage | Officer approve/reject/settle, the manager assign picker, dark mode and narrow layouts were not clicked through in a browser by the AI; no browser-level automated tests. |
| Session storage | The JWT is kept in `localStorage`. |

# Part F: Open Items

These items are not yet documented and should be completed before final review. **They are the author's to write; the AI has
not filled them in because it would have to invent them.**

1. **Pre-existing commits.** The repository already had commits before Phase 3 (`Initial commit` to `pagination added`).
   State which parts were AI-generated, which were written by hand, and how they relate to the Phase 1 build.
2. **Prompt 4 resolution.** Record the final fix for the `TypeTag :: UNKNOWN` compile error.
3. **Missing entries.** Prompts 6-23 and design sections B2-B5 are not yet written up in this journal.
4. **F1 prompt text.** Confirm that the edited block is what you want to show, or replace it with the message that was actually
   sent, or relabel it "cleaned-up prompt" (see the note under F1).
5. **Decisions for F1 to F6** are all `Pending author review`. Choose ACCEPTED, CHALLENGED or OVERRODE for each, with your reason.
6. **F3 was reconstructed.** Read it and correct anything that does not match what you remember of that session.
7. **Backend commit hashes.** The backend history in this repository has the same commits, messages, authors and dates as the
   original, but different hashes, because the committed `.build-cache/` was removed from every commit (see F5). If anyone asks, that
   is why `git log` here does not match the original repository.
8. **Docker run (F6).** Install Docker, follow `DOCKER.md`, and write what happened (including any fix needed) into F6. Decide whether to
   add `max.block.ms` to the backend's Kafka producer settings.
9. **Fresh-clone check.** Clone the repository somewhere clean, run `npm install` in `frontend/`, and follow `README.md` once; F5 records
   what was and was not run.

---

# Appendix: Template for New Entries

```text
### Prompt N: <title>
**Prompt:** ...
**Result:** what the AI produced.
**Decision (ACCEPTED / CHALLENGED / OVERRODE):** ... because ...
**Verification:** build, test or manual check performed.
```
