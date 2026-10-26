# CLAUDE.md — Claim Management UI

Guidance for Claude Code (and any human) working in the `frontend/` folder of this repository. Run every `npm` command from `frontend/`.
The backend is in `../backend`; the shared README, ARCHITECTURE and AI journal are in the repository root.

## What this is

Angular single-page app for the **Chubb APAC claims take-home assessment** (Fullstack Developer, 2–3 h target, 5 h hard cap).
Claimants report and track claims; claims officers pick up, review and decide them; managers see workload, officer
performance and liability exposure. **One Angular app serves all three roles** (the brief: "Both user types access the same
Angular application").

The **backend is finished and is the owner of the API contract.** This folder only consumes it.

| | |
|---|---|
| Backend | `../backend` in this repository (Spring Boot 3.4.5, Java 21, Maven) — **do not edit it as part of UI work** |
| Backend URL (local) | `http://localhost:8080` (`mvn spring-boot:run` in `backend/`; H2 in-memory, resets on restart) |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` (OpenAPI JSON: `/v3/api-docs`) |
| Backend docs | the root `README.md` and `ARCHITECTURE.md` (part B), `../docs/SWAGGER-TEST-GUIDE.txt`, `../backend/postman/Claims-Management.postman_collection.json` |

### Current state (as of 2026-10-26)

**All 22 backend endpoints are integrated** (19 claim-workflow and auth endpoints, plus 3 for optional claim documents) and every screen in
the brief→UI checklist below exists. Verified three ways:
312 unit tests (Vitest, mocked HTTP) across 20 spec files · a 28-step scripted lifecycle against the **real running backend** through the dev proxy (run
before the document endpoints were added; the documents were exercised against the real backend separately, by live API probes and in the browser) ·
a manual browser walkthrough (register → report → pick up → review → request info → answer → dashboard; and report with documents → claim page →
download → officer's read-only view).
`npm run build` is clean (≈316 kB initial, one lazy chunk per page). The screens were redesigned on 2026-10-26 (see *Look and feel* below)
and looked at in a browser in light mode, dark mode and at phone width.

Not yet verified in a real browser: officer approve/reject/settle and the manager's assign picker (covered by unit tests and the
scripted API run); dark mode and phone width were checked by eye, not systematically, and there is no accessibility audit. Files were handed to
the report form by a script (the built-in browser has no file chooser), so the real chooser dialog and drag-and-drop were not exercised. In Docker, `Dockerfile` and `nginx.conf` here serve the production build and forward
`/api` to the backend (see `../DOCKER.md`); no run result of that stack is recorded yet.

### Layout of `src/app`

```
core/       models.ts (API types) · api.service.ts (the ONLY file with URLs) · auth.service.ts · auth.interceptor.ts
            guards.ts · problem.ts (error → message) · claim-actions.ts (THE action table)
            claim-progress.ts (stages shown on a claim page) · markets.ts (the six markets) · dates.ts (7-day window, "3 h ago")
            policy-number.ts (generated policy reference) · documents.ts (attachment limits + the check that explains a refused file)
            save-file.ts (FileSaver: saves a downloaded Blob) · flash.ts (one message shown once on the next page)
shared/     status-badge.ts · amount.pipe.ts · icon.ts (SVG icons) · logo.ts (brand mark) · auth-shell.ts (sign-in frame)
shell/      signed-in frame (header, role navigation, user chip); calls GET /api/auth/me on load to re-validate the session
features/   login · register · claims (list, new-claim, claim-detail) · queue · dashboard
styles.css  the design system: tokens, light/dark themes, every shared class (components have no styles of their own)
testing.ts  fixtures for specs only (excluded from the app build)
```

Routes: `/login`, `/register`; signed-in: `/claims` (role-scoped list), `/claims/new` (claimant), `/claims/:id`, `/queue`
(officer, manager), `/dashboard` (manager). Landing page by role: claimant `/claims`, officer `/queue`, manager `/dashboard`.

## Tooling (read from this repo)

* Angular **22.2** (CLI 22.2.3), TypeScript ~6.0, RxJS ~7.8. Builder `@angular/build` (application + dev-server).
* **Zoneless**: no `zone.js` dependency; `app.config.ts` uses `provideBrowserGlobalErrorListeners()`.
* Tests: **Vitest** through `@angular/build:unit-test` (jsdom). Not Karma/Jasmine.
* Prettier (`.prettierrc`): `printWidth 100`, single quotes, Angular parser for `*.html`.
* File naming is the current Angular style: `app.ts` / `app.html` / `app.css` — **no `.component` suffix**. Selector prefix `app`.
* `package.json` pins `npm@11.17.0`. Angular 22 needs Node 22.22.3+, 24.15+ or 26+; built and tested here with Node 26.

```bash
npm install
npm start                    # ng serve -> http://localhost:4200, proxies /api -> http://localhost:8080
npm run build
npm test -- --no-watch       # ng test (Vitest); without --no-watch it stays in watch mode
npm test -- --no-watch --include src/app/core/claim-actions.spec.ts    # one spec file
```

Start the backend first (`mvn spring-boot:run` in `backend/`), otherwise sign-in shows "Cannot reach the
server". Local accounts: `officer@claims.local` and `manager@claims.local` (password `ChangeMe123!`); claimants use **Create an account**.
`npm install` prints `allow-scripts` warnings (npm 11 skips dependency install scripts); build and tests work regardless.

## Backend contract

Source of truth is Swagger and the root README. If this table and the backend disagree, the backend wins — fix this file.

### Auth

| Endpoint | Access | Body → response |
|---|---|---|
| `POST /api/auth/register` | public | `{email, password(8–72), fullName}` → `AuthResponse` (creates a **CLAIMANT**) |
| `POST /api/auth/login` | public | `{email, password}` → `AuthResponse` |
| `GET /api/auth/me` | any | → `{id, email, fullName, role}` |

`AuthResponse = {token, tokenType, expiresIn (ms; default 86 400 000), userId, email, fullName, role}`.
Send `Authorization: Bearer <token>` on everything except register/login. `role` is `CLAIMANT | OFFICER | MANAGER`.
Seeded local accounts: `manager@claims.local` and `officer@claims.local`, password `ChangeMe123!`. Claimants self-register.

### Claims

| Endpoint | Who | Notes |
|---|---|---|
| `POST /api/claims` | CLAIMANT | `CreateClaimRequest` (below) → `ClaimResponse` (201) |
| `GET /api/claims?page&size&status` | any | **Role-scoped**: claimant = own, officer = *assigned to them*, manager = all. Newest first. |
| `GET /api/claims/queue?page&size` | OFFICER, MANAGER | Unassigned `SUBMITTED` claims, **oldest first** |
| `GET /api/claims/{id}` | any | Claimant: only own. Officer: unassigned or own. Otherwise **404**. |
| `GET /api/claims/{id}/history` | same as view | `ClaimHistoryResponse[]` audit trail |
| `POST /api/claims/{id}/documents` | CLAIMANT (owner), claim not `REJECTED`/`SETTLED` | `multipart/form-data`, ONE file in field `file` → `ClaimDocumentResponse` (201). 5 files max per claim, 5 MB each, PDF/JPG/PNG (type judged from the file's bytes). 400 empty/nameless, 409 closed or full, 413 too big, 415 wrong type |
| `GET /api/claims/{id}/documents` | same as view | `ClaimDocumentResponse[]`, details only |
| `GET /api/claims/{id}/documents/{documentId}` | same as view | the file's bytes (`responseType: 'blob'`; a failure's JSON arrives inside the Blob, see `readBlobProblem`) |
| `PUT /api/claims/{id}/pickup` | OFFICER | no body; assigns the claim to the caller |
| `PUT /api/claims/{id}/assign` | OFFICER, MANAGER | `{officerId}`. Officer: only to self (else 403). Manager: any officer. |
| `PUT /api/claims/{id}/review` | assigned OFFICER, MANAGER | no body; → `UNDER_REVIEW` |
| `PUT /api/claims/{id}/request-info` | assigned OFFICER, MANAGER | `{note}` → `MORE_INFO_REQUIRED` |
| `PUT /api/claims/{id}/provide-info` | CLAIMANT (owner) | `{response}` → back to `UNDER_REVIEW` |
| `PUT /api/claims/{id}/approve` | assigned OFFICER, MANAGER | `{note}` → `APPROVED` |
| `PUT /api/claims/{id}/reject` | assigned OFFICER, MANAGER | `{note}` → `REJECTED` |
| `PUT /api/claims/{id}/settle` | assigned OFFICER, MANAGER | `{note}` → `SETTLED` (only from `APPROVED`) |

### Manager dashboard (MANAGER only)

`GET /api/dashboard/workload`, `GET /api/dashboard/liability`, `GET /api/dashboard/performance`.

### Shapes (mirror these exactly in `models`)

```
ClaimResponse        { id:number, claimNumber, policyNumber, claimantName, claimantEmail, incidentDate:'YYYY-MM-DD',
                       description, amountClaimed:number, claimType:'MOTOR'|'PROPERTY', market:string(2-letter),
                       status, claimantId, assignedOfficerId|null, assignedOfficerName|null,
                       informationRequest|null, informationResponse|null, decisionNotes|null,
                       createdAt, updatedAt }                       // ISO instants
CreateClaimRequest   { policyNumber(≤64), claimantName(≤120), claimantEmail(must equal the logged-in email),
                       incidentDate(not future), description(20–4000 chars), amountClaimed(>0, ≤2 decimals),
                       claimType, market(^[A-Za-z]{2}$) }
ClaimHistoryResponse { id, fromStatus|null, toStatus, changedById, changedByName, note|null, changedAt }
ClaimDocumentResponse { id, fileName, contentType, sizeBytes, uploadedAt, uploadedByName }
PageResponse<T>      { content:T[], page, size, totalElements, totalPages, first, last }   // page is 0-based, size ≤ server max
WorkloadResponse     { unassignedClaims, totalOpenClaims, submitted, underReview, awaitingInformation, approved, rejected, settled }
LiabilityResponse    { pendingExposure, approvedAwaitingSettlement,
                       byClaimType:[{key, pendingExposure, approvedAwaitingSettlement}], byMarket:[{…same…}] }
OfficerPerformance   { officerId, officerName, assignedClaims, activeClaims, resolvedClaims }   // lists ALL officers
Problem (RFC 7807)   { type, title, status, detail, instance, errors?: {field: message} }
ClaimStatus          SUBMITTED | UNDER_REVIEW | MORE_INFO_REQUIRED | APPROVED | REJECTED | SETTLED
```

Errors are `application/problem+json`. **Show `detail`** to the user (it is written to be shown). `errors` (400) maps field → message.
401 = missing/expired token → sign out. 403 = not allowed. 404 = not found *or not yours*. 409 = invalid status transition / already assigned.

### Status workflow (backend `TRANSITIONS` map in `ClaimService`)

```
SUBMITTED ──review──▶ UNDER_REVIEW ──approve──▶ APPROVED ──settle──▶ SETTLED
                         │   ▲  └──reject──▶ REJECTED
              request-info   └── review / provide-info
                         ▼
                 MORE_INFO_REQUIRED
```

`pickup` / `assign` set the officer **only — the status stays `SUBMITTED`** until the officer calls `review`.

## Backend facts that shape this UI (verified in the backend source — do not forget)

1. **The backend has no CORS configuration.** A browser on `:4200` calling `:8080` directly will be blocked. Use the Angular dev
   proxy (`proxy.conf.json`: `/api` → `http://localhost:8080`, wired into `angular.json` `serve.options.proxyConfig`) and call **relative
   `/api/...` URLs** everywhere. Never "fix" this by editing the backend from here.
2. **The backend does not tell the UI which actions are allowed.** The UI needs one small, central action table (status × role ×
   assignment) that mirrors the rules below. It is a *convenience*: the server stays the authority, so every action must also handle
   403/404/409 gracefully (show `detail`, reload the claim).
3. IDs are **numbers** (`Long`), not UUIDs. Route params must be parsed.
4. **No "list officers" endpoint.** The manager's assign-picker uses `GET /api/dashboard/performance`, which returns all officers
   (`officerId`, `officerName`).
5. **No currency field anywhere.** `amountClaimed` is a bare number and `market` is a 2-letter code. The dashboard's top-level
   `pendingExposure` / `approvedAwaitingSettlement` add amounts **across all markets** — i.e. mixed currencies. Show the
   `byMarket` lines as the primary view and label the grand total clearly as unconverted. Do not invent exchange rates.
6. `note` is mandatory (not blank, ≤ 2000) for request-info, approve, reject and settle. `review` and `pickup` take no body.
7. `claimantEmail` on create must equal the logged-in account's email — prefill it (and `claimantName` from `fullName`) from the session.
8. Claim lists are role-scoped (see table). A claimant's "My claims" is `GET /api/claims`; an officer's "My claims" is the same call;
   the officer's *work queue* is `GET /api/claims/queue`; a manager's "All claims" is `GET /api/claims`.

### UI action table (mirror of `ClaimService`; keep in ONE file)

| Viewer | Claim state | Offer |
|---|---|---|
| Claimant (owner) | `MORE_INFO_REQUIRED` | provide-info |
| Officer | `SUBMITTED`, unassigned (queue) | pickup |
| Officer, claim assigned to them | `SUBMITTED` | review |
| | `UNDER_REVIEW` | request-info, approve, reject |
| | `MORE_INFO_REQUIRED` | review (resume) |
| | `APPROVED` | settle |
| Manager (no assignment needed) | non-terminal | assign (to an officer from the performance list) |
| | same status rules as the officer | review / request-info / approve / reject / settle |
| Anyone | `REJECTED`, `SETTLED` | nothing (terminal) |

The backend does **not** forbid reassigning rejected/settled claims (its own `CLAUDE.md` lists that as a known gap); the UI should
simply not offer it.

The same file has `canAddDocuments(claim, viewer)`: true only for the owning claimant while the claim is not `REJECTED` or `SETTLED`
(what the backend allows). Everyone who can open a claim sees its documents and can download them; nobody else gets "Add documents".

## Brief → UI checklist

| Brief requirement | Backend support | UI status |
|---|---|---|
| Claimant reports an incident | `POST /api/claims` | done |
| Claimant tracks a claim (status, history) | `GET /api/claims`, `/{id}`, `/{id}/history` | done |
| Claimant provides additional information | `PUT /{id}/provide-info` | done |
| Claimant receives a decision | `decisionNotes` + status on the claim | done |
| Claimant attaches supporting documents (optional; added after the brief's checklist was written) | `POST/GET /{id}/documents` | done |
| Officer picks up incoming claims | `GET /queue`, `PUT /pickup` | done |
| Officer reviews / assesses | `PUT /review`, `/request-info` | done |
| Officer progresses to settlement or rejection | `PUT /approve`, `/reject`, `/settle` | done |
| Officer sees their workload | `GET /api/claims` (assigned) | done |
| Manager sees team workload & performance | `/api/dashboard/workload`, `/performance` | done |
| Manager sees outstanding liability exposure | `/api/dashboard/liability` | done |
| One app, three roles | JWT `role` | done |

"done" = built, unit-tested, and exercised against the real backend (see *Current state* for what was only unit/API-tested).

## Architecture rules for this repo

1. **One API layer.** A single `ApiService` (HttpClient, relative `/api/...`) is the only code that knows URLs. Components never build requests.
2. **Typed everything.** Interfaces for every request/response above; **no `any`** in API models.
3. **Standalone components, `inject()`, signals** for state; functional guards and interceptors; no NgModules; zoneless-safe code.
4. **Routes per role, lazy-loaded**; `authGuard` + `roleGuard(...)`. Guards steer navigation — the API is what enforces access.
5. **Interceptors:** attach the bearer token to `/api/` calls only (never to `login`/`register`); on 401 (not from login) sign out and go to `/login`.
6. **Errors:** one helper turns a Problem response into a user message (`detail`, or the joined `errors` for 400). No raw stack traces or JSON in the UI.
7. **Every screen has loading, empty and error states.** Disable submit buttons while a request is in flight.
8. **The action table (above) is the only place UI rules about "who can do what" live.** No role/status `if`s scattered in templates.
9. Show amounts with their **market** next to them; never present mixed-market sums as one currency without a caveat (fact 5).
10. Session: keep the token in memory + `localStorage`, guarded with `try/catch`; drop it when `expiresIn` has elapsed.
11. **Look and feel (redesigned 2026-10-26).** All styling is in `src/styles.css`: use its tokens (`--primary`, `--surface`, `--border`, `--radius`,
    ...) and existing classes (`card`, `btn`, `badge`, `chip`, `stat`, `stepper`, `timeline`, `form-section`, `empty-state`, `loading`) rather than
    inventing colours or adding component styles; every colour must work in light and dark. Icons come from `shared/icon.ts` (add a path there,
    not a library). Status is never shown by colour alone. Keep the markup hooks the specs rely on (`button.primary` is the submit button, `[role=alert]`
    and `[role=status]` appear once, `h1` holds only the heading text, `.actions` holds only action buttons, `data-testid` attributes).
12. **Rules that are UI-only live in one small file each**: the market list (`core/markets.ts`), the 7-day incident window (`core/dates.ts`), the generated
    policy number (`core/policy-number.ts`). The backend does not enforce them (it accepts any two-letter market, any past date, any policy number), so
    never describe them as API rules; if one must hold, it has to be added to the backend.
13. **The report form explains every problem.** A missing or invalid required field shows its own message next to it (`aria-invalid`,
    `aria-describedby`), an error summary (`role="alert"`) lists them as links in form order, and focus goes to the first one; server 400 `errors`
    are shown against their fields the same way. Documents are optional and are sent after the claim is created, one request each: a failed
    upload never undoes the claim, it is reported on the claim page (flash message) where the claimant can retry. The document limits in
    `core/documents.ts` copy the backend's (5 files, 5 MB, PDF/JPG/PNG) so a mistake is explained before sending; the backend has the last word.

## Conventions

* Format with Prettier (`.prettierrc`). Keep files small and named by feature: `features/<area>/<name>.ts|html|css`.
* Tests with Vitest: services via `HttpTestingController`, components via `TestBed`. A behaviour change needs a test change in the same commit.
* Commits: small, conventional (`feat(ui): …`, `test(ui): …`, `docs: …`), one concern each. **Commit only when asked.**
* **Update the root `AI_JOURNAL.md` (Part C, next free F-number) in the same change as any non-trivial AI-driven step.** Be honest: record what was corrected or rejected, not just what worked.
* Ask before installing or downloading anything. Never commit tokens or passwords.
* Do not modify `backend/` as part of UI work. If the backend needs a change (e.g. CORS, an officers endpoint), write it down in
  the root `AI_JOURNAL.md` under Part D (*Open Questions for the Backend*) and ask the author.
