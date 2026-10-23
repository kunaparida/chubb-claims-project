# AI-Assisted Development Record

## Project overview

| Item | Details |
|---|---|
| Project | Claims Platform — `backend/` and `frontend/` |
| Backend | Java 21, Spring Boot 3.4.5, Spring Security/JWT, Spring Data JPA, H2 |
| Frontend | Angular 22, standalone components, signals, zoneless configuration, Vitest |
| Messaging | Transactional outbox; logging publisher by default, Kafka publisher configurable |
| Purpose | Summarise material AI-assisted work, engineering decisions, and the verification evidence recorded during development |
| Last updated | 23 October 2026 |

This journal focuses on AI assistance that materially influenced the design, changed the repository, exposed a defect, or required correction. Routine prompts and verbatim prompt transcripts are intentionally omitted. The author remains responsible for reviewing the code and decisions. AI-generated output was treated as a proposal until supported by code inspection, tests, builds, or manual checks.

## 1. Summary

AI assistance was used across four main areas:

1. **Backend baseline and testing:** scaffolded the initial claims API, proposed service and security tests, and helped diagnose a Mockito/Byte Buddy compatibility issue encountered on JDK 27.
2. **Backend design:** supported the implementation and review of claim-status history, a transactional outbox, configurable event publishing, and claim type/market fields.
3. **Frontend integration:** helped structure the Angular application, connect the UI to the backend's 19 endpoints, implement role-aware navigation and claim actions, and prepare end-to-end validation instructions.
4. **Repository and container documentation:** consolidated the backend and frontend into one repository, aligned root-level documentation, and drafted Docker/Kafka configuration and operating instructions.

## 2. Material design decisions

| Area | Decision and rationale | Status / limitation |
|---|---|---|
| Application architecture | Use a layered modular monolith. Claim changes, audit history, and outbox records can be committed in one database transaction, while deployment and local debugging remain straightforward. | Implemented in the documented backend design. |
| Development database | Use in-memory H2 to keep local setup simple and avoid requiring a separately installed database. | Data is lost on restart. PostgreSQL is not currently configured as an active dependency. |
| Claim workflow | Enforce valid status transitions in the service layer and reject invalid transitions with a conflict response. | Backend tests and the recorded lifecycle replay cover workflow behaviour. |
| Audit and events | Write claim-status history and outbox records within the claim transaction; publish events asynchronously through a configurable publisher. | Logging publisher is the default. Kafka code is wired and unit-tested, but a successful real-broker run is not recorded. |
| Market representation | Keep `market` as a validated two-letter code rather than inventing a fixed market enum. | `claimType` and `market` are required claim-submission fields. Currency is not represented. |
| Frontend/API boundary | Treat the backend API and its OpenAPI contract as authoritative. Keep API URLs in one frontend service and use a development proxy for relative `/api` requests. | The UI mirrors backend workflow rules for button visibility; the server remains the security authority. |
| Role-aware UI | Use a shared Angular application with role-specific navigation and guarded routes for claimants, officers, and managers. | Route guards improve navigation but do not replace backend authorization. |
| Docker and Kafka | Add container definitions for the backend, frontend, Kafka broker, and topic-initialisation job, plus operating instructions. | Configuration was reviewed, but the recorded development session did not build or run the Docker stack. Do not treat container execution as verified. |

## 3. Defects and corrections recorded

- **Build compatibility:** seven tests initially failed because the managed Byte Buddy version could not mock classes on the JDK 27 environment. The dependency version was pinned and the build subsequently passed in that environment.
- **Frontend work-queue error handling:** a failed claim pick-up (`409 Conflict`) could lose its error message during list reload. The reload flow was adjusted to preserve the message and a regression test was added.
- **Frontend test setup:** early test failures exposed test configuration issues, including TestBed setup order, cancelled requests, and interceptor registration. The specifications were corrected.
- **Backend-unavailable messaging:** the Angular proxy can return `502`, `503`, or `504` when the backend is unavailable. These responses are now mapped to a clearer connection error rather than a generic failure.
- **Session revalidation:** the UI uses `GET /api/auth/me` on application startup to check the stored session and signs the user out when the API rejects the token.
- **Documentation corrections:** setup requirements and UI behaviour were checked against the project configuration to correct inaccurate statements in early documentation drafts.

## 4. Verification evidence recorded during development

The following results are transcribed from the development journal and project documentation. They are **not a fresh test run performed while preparing this document**.

| Check | Recorded result | Scope / qualification |
|---|---|---|
| Backend test suite | 82 tests passed, 0 failures | `mvn test` / `mvn clean verify` results recorded for the backend. |
| Frontend unit/component tests | 216 tests passed across 15 spec files | Vitest results recorded during frontend development. |
| Frontend production build | Successful strict production build; initial bundle reported at approximately 293 kB | The recorded bundle size is environment/build dependent. |
| API contract comparison | 19 backend endpoints matched to the frontend API service | Recorded comparison against the live OpenAPI document. |
| Backend lifecycle replay | Expected HTTP responses recorded, including `200`, `201`, `400`, `403`, and `409` | The journal records a successful Postman/API lifecycle replay. |
| Frontend scripted lifecycle | 45 checks reported as passing | The result is recorded in the journal; it was not independently rerun for this documentation update. |
| Browser walkthrough | A limited manual walkthrough was recorded | Not every officer action, manager assignment flow, responsive layout, or dark-mode state was manually checked. |
| Docker Compose / Kafka broker | No successful run result recorded | Docker images, containers, and real-broker event delivery remain unverified in the recorded evidence. |

## 5. Known limitations and follow-up items

- **Data persistence:** H2 is in-memory and resets when the backend restarts. Use a persistent database and migrations before production deployment.
- **Development security settings:** the H2 console is enabled and can be reachable without application authentication, including from other hosts in the documented configuration. Disable it and remove or restrict its security exception outside local development.
- **Secrets and demo accounts:** development JWT secrets and seeded credentials must not be reused in a shared or production environment. Use managed secrets and real account provisioning.
- **Kafka validation:** run the Docker Compose stack and verify event delivery against the actual broker. The journal does not claim that this has passed.
- **Event relay resilience:** with multiple backend instances, the outbox relay may publish duplicates without coordination. Consumers should be idempotent; relay locking or a dedicated publisher may be needed.
- **Concurrent updates:** optimistic locking is not implemented, so simultaneous updates may race. Consider `@Version` and a conflict response.
- **Money and markets:** no currency is stored; dashboard totals across markets are therefore labelled as unconverted. Do not interpret the cross-market total as a single-currency financial figure.
- **Frontend security and parity:** the token is stored in `localStorage`, and the frontend's action table duplicates backend workflow rules. Consider a safer token strategy and server-provided allowed actions.
- **Browser and accessibility coverage:** automated browser tests and a formal accessibility audit are not recorded. Responsive layouts, dark mode, officer approve/reject/settle actions, and manager assignment should receive a final manual/browser-level check.
- **Fresh-clone validation:** run the setup instructions from a clean checkout, including a first-time frontend dependency installation.

## 6. Accountability statement

This document distinguishes implementation and test results recorded during development from items that remain unverified. AI assistance supported implementation, troubleshooting, and documentation; it does not replace code review, security review, or deployment validation. The project owner should confirm that the decisions and AI-use summary accurately represent the work before submission.
