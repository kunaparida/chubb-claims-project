# Architecture and Design Decisions

A claims platform: claimants report and track incidents, officers work claims to a decision and settlement, managers see
workload, liability and performance. It is delivered as **one repository with two applications** that meet at a single
contract, the HTTP API.

| Part | Covers |
|---|---|
| [A. System overview](#part-a-system-overview) | The two applications, how a request travels, who owns what |
| [B. Backend](#part-b-backend) | Layers, domain model, workflow, sync vs event-driven, security, errors, tests |
| [C. Frontend](#part-c-frontend) | Structure, routing, state, API layer, session, errors, the action table, tests |
| [D. Integration decisions](#part-d-integration-decisions) | Contract ownership, dev proxy, who-can-do-what, officers, money, history |
| [E. Known limitations and next steps](#part-e-known-limitations-and-next-steps) | Backend, frontend and whole-system gaps, each with a next step |

---

## Part A: System overview

```
 Browser
   │  http://localhost:4200
   ▼
 Angular dev server (ng serve) ── serves the single-page app
   │  relative calls:  /api/...
   ▼
 Dev proxy (frontend/proxy.conf.json:  /api → http://localhost:8080)
   │
   ▼
 Spring Boot service :8080 ──► H2 in-memory database (PostgreSQL config provided)
   │                           outbox_events ─► scheduled relay ─► log (default) or Kafka
   └─ Swagger UI / OpenAPI at /swagger-ui/index.html, /v3/api-docs
```

| | Backend | Frontend |
|---|---|---|
| Folder | `backend/` | `frontend/` |
| Stack | Java 21, Spring Boot 3.4.5, Spring Security + JWT, Spring Data JPA, H2, optional Kafka | Angular 22 (zoneless, signals, standalone components), RxJS, Vitest |
| Owns | The API contract, every business rule, access control, audit history, events | Presentation, navigation, and a convenience copy of the "who can do what" rules |
| Authority | **Final.** Every rule is enforced here | **None.** Guards and hidden buttons only steer; the server answers 403/404/409 regardless |

**The backend owns the contract.** The frontend was built against the finished backend's Swagger and README and never changes
them. Where the backend lacks something the UI would like (CORS, an officers endpoint, "allowed actions", a currency), the UI works
around it and the gap is written down in [part D](#part-d-integration-decisions) and [part E](#part-e-known-limitations-and-next-steps).

**One app, three roles.** The brief asks for both user types to use the same Angular application. The JWT carries the role;
the shell shows role-specific navigation and each page is behind a role guard.

---

## Part B: Backend

### B.1 Shape of the system

**One deployable service (modular monolith), layered.**

```
                  HTTPS + Bearer JWT
 Client ──────────────────────────────────► JwtAuthenticationFilter ► SecurityConfig
                                                      │
                          ┌───────────────────────────▼───────────────────────────┐
                          │ controller  (role checks, validation, status codes)   │
                          ├───────────────────────────────────────────────────────┤
                          │ service     (transactions, business rules, state       │
                          │              machine, ClaimAuditRecorder)              │
                          ├───────────────────────────────────────────────────────┤
                          │ repository  (Spring Data JPA, aggregate queries)       │
                          └───────────────────────────┬───────────────────────────┘
                                                      ▼
                          H2 (default)  /  PostgreSQL (config provided)
                          tables: users, claims, claim_status_history, outbox_events
                                                      │
                  OutboxPublisher (scheduled) ────────┘──► EventPublisher
                                                            ├─ LoggingEventPublisher (default)
                                                            └─ KafkaEventPublisher (app.events.publisher=kafka)
```

Packages (`com.claims`): `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `security`, `config`,
`exception`, `validation`, `util`, `event`.

#### Why one service, not several
| Driver | Assessment |
|---|---|
| Consistency | A claim, its status, its assignee, its history and its outgoing event must change atomically. One database transaction gives that for free; separate services would need sagas. |
| Size and team | One bounded context ("claim lifecycle"), one team, a 2-3 hour budget. |
| Scaling | No evidence yet that any part needs independent scaling. |
| Operations | One artefact to build, run and debug. |

**Where I would split first, and why:** *notifications* and *reporting/dashboards*. They only need to know that a claim
changed, they tolerate seconds of delay, and dashboards have a different read profile (large aggregates) from claim
writes. The seam already exists: the `ClaimStatusChanged` event.

### B.2 Domain model

```
users ─┬─< claims >─┬─< claim_status_history
       │            └─ (assigned_officer_id → users)
       └─ (claimant_id → users)                    outbox_events (aggregate = Claim, id = claim id)
```

| Entity | Key fields |
|---|---|
| `User` | email (unique), password hash, fullName, role (CLAIMANT / OFFICER / MANAGER) |
| `Claim` | claimNumber (unique), policyNumber, claimant details, incidentDate, description, amountClaimed, **claimType** (MOTOR / PROPERTY), **market** (2-letter code), status, claimant, assignedOfficer, informationRequest, **informationResponse**, decisionNotes, timestamps |
| `ClaimStatusHistory` | claim, fromStatus (null for the first record), toStatus, changedBy, note, changedAt: append-only audit trail |
| `OutboxEvent` | aggregateType, aggregateId, eventType, JSON payload, createdAt, publishedAt (null = not yet published) |

`market` is a validated 2-letter code, not an enum, because the list of markets is business data I do not own.

#### Claim lifecycle (state machine)

```
SUBMITTED ──► UNDER_REVIEW ──► APPROVED ──► SETTLED
                  │  ▲
                  ▼  │
          MORE_INFO_REQUIRED          UNDER_REVIEW ──► REJECTED
```
Allowed: `SUBMITTED→UNDER_REVIEW`, `UNDER_REVIEW→MORE_INFO_REQUIRED|APPROVED|REJECTED`, `MORE_INFO_REQUIRED→UNDER_REVIEW`,
`APPROVED→SETTLED`. `REJECTED` and `SETTLED` are terminal. Anything else is `409 Conflict`.
The rules live in one table in `ClaimService`. `MORE_INFO_REQUIRED → UNDER_REVIEW` is triggered by the **claimant**
(`provide-info`) or by an officer (`review`).

Picking up or assigning a claim sets the officer only: the status stays `SUBMITTED` until the officer calls `review`.

### B.3 What is synchronous (REST) and what is event-driven

| Concern | Style | Reason |
|---|---|---|
| Submit, read, list, queue, pick up, assign | REST (sync) | Caller needs an immediate, consistent answer. |
| Every status change (review, request-info, provide-info, approve, reject, settle) | REST (sync) command | Must validate against current state and answer success or conflict. |
| "A claim changed status" | **Event** (`ClaimStatusChanged`) | Consumers (claimant notifications, reporting, audit exports) are asynchronous, can fail and retry independently, and must never slow or fail the claim update. |
| Dashboards | REST read | Aggregate queries today; first candidate for a read model if volume grows. |

#### Outbox pattern (how events are produced)
1. In **one database transaction** the service updates the claim, appends a `claim_status_history` row and inserts an
   `outbox_events` row (`ClaimAuditRecorder`, `Propagation.MANDATORY`).
2. A scheduled `OutboxPublisher` reads unpublished rows oldest-first and calls `EventPublisher.publish(topic, claimId, json)`.
3. Only after a successful publish is `publishedAt` set. A failure stops the batch (keeps order) and the row is retried.

Guarantees and limits:
- **No lost events and no phantom events:** the event exists if and only if the status change committed.
- **At-least-once delivery:** a crash between send and commit re-sends the row. Consumers de-duplicate on `eventId`.
- **Ordering per claim:** Kafka record key is the claim id, so one claim's events stay in one partition, in order.
- **Default publisher** logs the event, so the app runs with no broker. `app.events.publisher=kafka` plus
  `spring.kafka.bootstrap-servers` switches to Kafka (`KafkaEventPublisher` waits for the broker acknowledgement).

Event contract (`claims.claim-status-changed`, JSON):
```json
{"eventId":"uuid","claimId":1,"claimNumber":"CLM-...","claimType":"PROPERTY","market":"HK",
 "amountClaimed":1250.00,"fromStatus":"UNDER_REVIEW","toStatus":"APPROVED",
 "changedBy":"officer@claims.local","occurredAt":"2026-10-14T06:45:17Z"}
```
`fromStatus` is null for the initial submission.

### B.4 Read/write profile and what officers need
- **Writes** are low volume, transactional, per claim. **Reads** are dominated by lists and dashboards.
- Officers need: *what is waiting* (`GET /api/claims/queue`: unassigned `SUBMITTED`, oldest first), *what is mine*
  (`GET /api/claims`, optional `?status=`), and *what happened* (`GET /api/claims/{id}/history`).
- Indexes: explicit indexes exist on `claim_status_history.claim_id` and `outbox_events.publishedAt`. H2 indexes foreign
  keys automatically; **on PostgreSQL I would add explicit indexes** on `claims(claimant_id)`, `claims(assigned_officer_id)`
  and `claims(status)`, because the list, queue and dashboard queries filter on them.
- Managers: workload (counts per status, unassigned), liability (pending exposure and approved-not-settled, **broken down
  by claim type and market**) and officer performance, all computed by the database.

### B.5 Security model
JWT (HS256) in `Authorization: Bearer`, stateless; BCrypt passwords; method security with `@PreAuthorize`; RFC 7807
`application/problem+json` for every error including 401 and 403.

| Endpoint | CLAIMANT | OFFICER | MANAGER |
|---|---|---|---|
| `POST /api/claims` | yes (own account email) | no | no |
| `GET /api/claims`, `GET /api/claims/{id}` | own claims | assigned claims; may open unassigned (queue) claims | all |
| `GET /api/claims/queue` | no (403) | yes | yes |
| `PUT .../pickup` | no | yes | no |
| `PUT .../assign` | no | self only | any officer |
| `PUT .../review`, `request-info`, `approve`, `reject`, `settle` | no | assigned officer | yes |
| `PUT .../provide-info` | owner, only when info requested | no | no |
| `GET .../history` | own claims | same as view | all |
| `GET /api/dashboard/*` | no | no | yes |

Other claimants' or officers' claims return **404**, not 403, so existence is not leaked. Public registration can only
create claimants.

### B.6 Error model
RFC 7807: `type`, `title`, `status`, `detail`, `instance`, and an `errors` map for field validation.
`400` validation or bad input, `401` no/invalid token, `403` role or ownership, `404` not found or not visible,
`405`/`415` wrong method or content type, `409` invalid state transition or conflict, `500` generic (details only in logs).

### B.7 Testing strategy
- Unit tests with Mockito for services, the audit recorder, the outbox relay and the publishers.
- Validation tests for request rules (policy, amount, future date, description length, claim type, market).
- Two Spring Boot integration tests on H2: the original role/workflow flow and the lifecycle extensions (queue, pick-up,
  claimant answer, history, filter, outbox, liability breakdown).
- Wiring tests prove the publisher is chosen by configuration.
- Postman collection (`backend/postman/`) replays the same flow, with assertions, in collection order.

---

## Part C: Frontend

### C.1 Structure

```
frontend/src/app/
  core/       models.ts            API types, mirrored from the backend (no `any`)
              api.service.ts       the ONLY file that knows URLs: one method per endpoint (19)
              auth.service.ts      session signal, login / register / logout, GET /api/auth/me re-validation
              auth.interceptor.ts  bearer token on /api/ calls; 401 → sign out
              guards.ts            authGuard, roleGuard(...), homeRedirectGuard
              problem.ts           HTTP error → one human-readable message
              claim-actions.ts     THE action table: who can do what to a claim
  shared/     status-badge.ts, amount.pipe.ts
  shell/      signed-in frame and role navigation
  features/   login · register · claims (list, new-claim, claim-detail) · queue · dashboard
  testing.ts  fixtures used only by specs (excluded from the app build)
```

| Decision | Reason |
|---|---|
| Standalone components, `inject()`, signals, functional guards and interceptors, no NgModules | Current Angular style; less boilerplate; state changes drive the view without change-detection plumbing. |
| **Zoneless** (`provideBrowserGlobalErrorListeners()`, no `zone.js`) | Smaller bundle, and signals already tell Angular when to re-render. |
| One lazy-loaded chunk per page | The claimant never downloads the dashboard code. Initial bundle is about 293 kB; each page is a separate chunk. |
| File names without the `.component` suffix (`claim-detail.ts`) | The current Angular CLI convention; selector prefix `app`. |

### C.2 Routes and role landing pages

| Route | Who | Page |
|---|---|---|
| `/login`, `/register` | public | Sign in (a demo-accounts panel fills in the seeded emails); create a claimant account |
| `/claims` | any signed-in role | Role-scoped list: claimant = mine, officer = assigned to me, manager = all |
| `/claims/new` | claimant | Report a claim (name and email prefilled from the session) |
| `/claims/:id` | any (server decides visibility) | Detail, status history, and the actions the viewer may take |
| `/queue` | officer, manager | Unassigned submitted claims, oldest first; pick up (officer) or assign (manager) |
| `/dashboard` | manager | Workload, liability by market, officer performance |

After sign-in a claimant lands on `/claims`, an officer on `/queue`, a manager on `/dashboard`. Route guards (`authGuard`,
`roleGuard`) are navigation conveniences; **the API is what enforces access.**

### C.3 API layer and errors

* **`ApiService` is the only code that builds a request.** It uses relative `/api/...` URLs only (see [D.2](#d2-no-cors-so-a-dev-proxy)). Components call
  methods and receive typed observables.
* **One error helper (`problem.ts`)** turns a failure into a message: the RFC 7807 `detail` if present; the joined `errors` map for a
  400; "cannot reach the server" for status 0 *and* for 502/503/504, because the dev proxy answers 502 with an empty body when the
  backend is down; a generic fallback otherwise. No stack traces or JSON ever reach the screen.
* **Every screen has loading, empty and error states**, and submit buttons are disabled while a request is in flight.

### C.4 Session

The session (`token`, user, `expiresAt = login time + expiresIn`) is held in a signal and mirrored to `localStorage` (key
`claims.ui.session`), every storage access wrapped in `try/catch`. An expired session is dropped on load. On start-up the shell
calls `GET /api/auth/me` to re-validate: a token the server no longer accepts ends the session instead of showing a broken
page. The interceptor attaches `Authorization: Bearer ...` to `/api/` URLs only, never to login or register and never to
another origin; a 401 from any other call signs the user out and returns to `/login` (a 401 *from* login simply means wrong
credentials).

### C.5 The action table (who can do what)

The backend does not return "allowed actions", so `core/claim-actions.ts` is the single place that decides which buttons a viewer
sees. It is a mirror of the backend's `ClaimService` rules:

| Viewer | Claim state | Offered |
|---|---|---|
| Claimant (owner) | `MORE_INFO_REQUIRED` | provide information |
| Officer | `SUBMITTED`, unassigned | pick up |
| Officer, claim assigned to them | `SUBMITTED` | start review |
| | `UNDER_REVIEW` | request info, approve, reject |
| | `MORE_INFO_REQUIRED` | resume review |
| | `APPROVED` | settle |
| Manager | any non-terminal claim | assign to an officer; plus the same status actions as an officer, no assignment needed |
| Anyone | `REJECTED`, `SETTLED` | nothing (terminal) |

Rules for this table: no role or status `if` statements in templates; a spec checks every role × status combination against the
backend workflow; and because the table is only a convenience, every action still handles 403, 404 and 409 by showing the server's
`detail` and reloading the claim.

### C.6 Money

The backend has **no currency field**: `amountClaimed` is a bare number and `market` is a 2-letter code. So every amount is shown
with its market (`amount` pipe), the dashboard leads with the per-market lines, and the grand totals (which the backend adds
across markets) are labelled **"unconverted, mixed markets"**. No exchange rates are invented.

### C.7 Testing strategy

* **Vitest** (via `@angular/build:unit-test`, jsdom), **216 tests** across 15 spec files.
* The API service is tested with `HttpTestingController` for every endpoint's verb, path and body; the interceptor, guards, error
  helper, session handling and the action table are tested as pure units; each page is tested through `TestBed` (rendering, the
  actions it offers, and failure handling).
* Beyond unit tests: a scripted run of the full lifecycle against the **real backend** through the dev proxy (45 checks, with the
  numbers used in `docs/E2E-VALIDATION-FLOW.txt`) and a manual browser walkthrough (register, report, pick up, review, request info,
  answer, dashboard). See [AI_JOURNAL.md](AI_JOURNAL.md) for what was and was not checked.

---

## Part D: Integration decisions

### D.1 The backend owns the contract

The frontend mirrors the API shapes exactly (`core/models.ts`): numeric ids, ISO date and instant strings, a 0-based page
(`PageResponse<T>`), RFC 7807 problems. If the UI and the backend ever disagree, the backend (Swagger and its README) wins and the UI
is fixed. The UI never edits the backend.

### D.2 No CORS, so a dev proxy

The backend has no CORS configuration, so a browser on port 4200 calling port 8080 directly is blocked. The frontend therefore
calls **relative** `/api/...` URLs and the Angular dev server proxies them (`frontend/proxy.conf.json`, wired into
`angular.json`). It is not a workaround to hide: the same relative URLs work unchanged behind any same-origin reverse proxy or
gateway in production, which is the recommended deployment. Adding CORS to the backend is the alternative if the SPA is ever
served from another origin.

### D.3 Status changes are commands, not edits

Each transition is its own `PUT` (`review`, `request-info`, `approve`, ...), and the UI offers exactly those that the action table
allows for the viewer. `pickup` and `assign` set the officer only, so after picking up, a claim is still `SUBMITTED` and the officer
must press *Start review*; the UI says so rather than pretending the claim moved.

### D.4 No "list officers" endpoint

The manager's assign picker is populated from `GET /api/dashboard/performance`, which returns every officer (`officerId`,
`officerName`) including those with no claims. A dedicated read-only officers endpoint would be cleaner (see part E).

### D.5 Notes and history

`note` is mandatory (not blank, at most 2000 characters) for request-info, approve, reject and settle; review and pickup take no
body. The claim holds only the **latest** decision note: settling overwrites the approval note in `decisionNotes`. The full
sequence of notes is in `GET /claims/{id}/history`, which the claim page shows as the timeline, so the UI shows both.
Claimants see the officer's information request, the decision, and the history of their own claim only.

### D.6 Identity on submit

`claimantEmail` on `POST /api/claims` must equal the signed-in account's email, so the new-claim form prefills it (and the
name) from the session and the backend rejects a mismatch.

### D.7 Consistent error and status semantics

`401` = session invalid, so sign out. `403` = not allowed. `404` = not found **or not yours** (the backend does not say which, so
the UI cannot either). `409` = invalid transition or already assigned. `400` = per-field `errors`. In every case the server's
`detail` is shown when there is one; otherwise the UI falls back to "You do not have permission to do that." (403) or "That could not
be found." (404). After a failed action the claim is reloaded so the screen shows its true state.

---

## Part E: Known limitations and next steps

### Backend (deliberate, for the sprint)
| Limitation | Next step |
|---|---|
| No optimistic locking: two officers acting at the same instant could race | Add `@Version` to `Claim`, map the conflict to 409 |
| Kafka path wired and unit-tested but **not run against a real broker** | Docker Compose with Kafka + an integration test (Testcontainers) |
| Outbox poller runs in every instance, so duplicates are possible with several instances | Row locking (`FOR UPDATE SKIP LOCKED`) or a dedicated relay |
| `ddl-auto=update`, in-memory H2, bootstrap users: **all data is lost on restart** | Flyway migrations, PostgreSQL, real user provisioning |
| `assignedOfficer` can be re-assigned without a history entry; rejected and settled claims can still be reassigned | Record assignment events; refuse assignment of terminal claims |
| Performance dashboard counts only; no time-to-decision | Use `claim_status_history` timestamps |
| No claimant notifications | Consumer of `ClaimStatusChanged` (the seam exists) |
| JWT: no refresh or revocation; default dev secret | Short-lived access tokens + refresh, secret from a vault |
| No rate limiting on login | Gateway or filter-level throttling |

### Frontend and integration
| Limitation | Next step |
|---|---|
| The backend has no CORS configuration, so the UI works only through the dev proxy or a same-origin gateway | Serve the built SPA behind a same-origin gateway, or add CORS for the real origin |
| The action table is a **copy** of backend rules and can drift | Backend returns `allowedActions` on each claim; the table disappears |
| No officers endpoint; the manager picker borrows the performance endpoint | `GET /api/users?role=OFFICER` |
| No currency on claims: amounts are shown by market and the cross-market totals are labelled unconverted | Add `currency` to the claim and convert (or stop summing) on the server |
| The session token is in `localStorage`, readable by any script on the page (XSS) | Short-lived access token in memory plus a refresh token in an httpOnly cookie |
| Officer approve/reject/settle and the manager assign picker were covered by unit tests and a scripted API run, **not clicked through in a browser by the author of the code**; dark mode and narrow/mobile layouts were not checked | Run `docs/E2E-VALIDATION-FLOW.txt` in a browser; add Playwright end-to-end tests |
| No browser-level automated tests; no accessibility audit | Playwright plus an axe pass in CI |
| Lists and the queue are paged (10 per page) but never auto-refresh, so a second officer picking up a claim is only noticed on reload or on a 409 | Periodic refresh or server-sent events for the queue |
