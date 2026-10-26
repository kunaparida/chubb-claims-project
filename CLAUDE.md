# CLAUDE.md — Claims Platform Repository Guide

Internal development guidance for AI coding assistants and contributors. This file is a repository index; implementation-specific guidance lives beside each application. It is not a substitute for the client-facing README or architecture document.

## What this is

One repository, three applications, one set of documents:

| Path | What | Rules for working in it |
|---|---|---|
| `backend/` | Spring Boot 3.4 / Java 21 REST API on in-memory H2 (owner of the API contract) | [backend/CLAUDE.md](backend/CLAUDE.md) |
| `frontend/` | Angular 22 single-page app for claimants, officers and managers | [frontend/CLAUDE.md](frontend/CLAUDE.md) |
| `notification-service/` | Spring Boot Kafka consumer that emails the claimant on each status change (no database, no business API) | [notification-service/CLAUDE.md](notification-service/CLAUDE.md) |
| `README.md` | How to run it, accounts, API summary | keep it the single entry point |
| `ARCHITECTURE.md` | Numbered sections 1 to 12: system context, decisions, components, backend, domain, lifecycle, events and the notification service (7), security, frontend, integration, testing, limitations (12) | update it when a design decision changes |
| `AI_JOURNAL.md` | How AI was used: backend Parts A–B, frontend and packaging Part C (F-numbers), open questions D, limitations E, open items F | see rule 4 |
| `docs/` | `E2E-VALIDATION-FLOW.txt` (manual test script), `SWAGGER-TEST-GUIDE.txt` | keep the numbers in the E2E script true when behaviour changes |
| `docker-compose.yml`, `DOCKER.md`, `*/Dockerfile`, `frontend/nginx.conf` | Containers: Kafka + backend + notification service + Mailpit + frontend | keep `DOCKER.md` and the compose file in step; the topic names appear in compose in `kafka-init` and in the environment of both Kafka users and must match `EVENTS_TOPIC` / `DEAD_LETTER_TOPIC` |

## Common commands

| Task | Command (run from the folder shown) |
|---|---|
| Run the backend (port 8080) | `mvn spring-boot:run` in `backend/` |
| Backend tests (118) and coverage report | `mvn test` in `backend/` |
| Notification service tests (76; they start a real Kafka broker and SMTP server in the JVM, about a minute) | `mvn test` in `notification-service/` |
| Run the UI (port 4200, proxies `/api` to 8080) | `npm install` once, then `npm start` in `frontend/` |
| UI tests (312) and production build | `npm test -- --no-watch`, `npm run build` in `frontend/` |
| Everything in containers (Kafka included) | `docker compose up --build -d` in the repository root, then see `DOCKER.md` |

## Rules that apply to everything

1. **The backend owns the contract.** UI work never edits `backend/`; if the backend needs a change, record it in `AI_JOURNAL.md` Part D
   and ask the author. A backend change that alters an endpoint also changes `frontend/` (API service, models, tests) in the same commit.
2. **Run commands from the folder they belong to.** Start the backend first; the UI reaches it through the dev proxy
   (`frontend/proxy.conf.json`), or through nginx in Docker, so always browse `http://localhost:4200`.
3. **Tests are part of the change.** A behaviour change needs a test change in the same commit, and the counts above and in the
   documents are kept true.
4. **Keep the journal short and honest.** Update `AI_JOURNAL.md` in the same change as any AI-driven step that **mattered**: it set the
   design, found or fixed a defect, changed the repository, or the AI was corrected. Do not log routine prompts. Record what was corrected
   or rejected, not just what worked, and what was *not* verified. Leave `Decision` lines for the author (`Pending author review`);
   never invent the author's decisions or fill in history you did not witness.
5. **The documents must match the project.** When code, configuration, ports, accounts or test counts change, fix `README.md`,
   `ARCHITECTURE.md` and `DOCKER.md` in the same change; check claims against the source instead of from memory.
6. **Commit only when asked.** Small, conventional commits (`feat(api): …`, `feat(ui): …`, `test: …`, `docs: …`), one concern each.
7. **Ask before installing or downloading anything. Never commit tokens, passwords or build output** (`target/`, `dist/`,
   `node_modules/`, `.build-cache/` are ignored by the root `.gitignore`).
8. **The database is in-memory H2.** It resets on every backend restart (restart before running `docs/E2E-VALIDATION-FLOW.txt`).
   PostgreSQL is *not* used and has no driver in `pom.xml`; do not add one unless asked. The H2 console (`/h2-console`) is **off by
   default** (`H2_CONSOLE_ENABLED=true` turns it on, and `SecurityConfig` then opens it); it needs no token, so local development only.
9. **New or changed endpoints are documented in the same change.** Every controller method needs an `@Operation` summary and its error
   `@ApiResponse`s, request fields need `@Schema` examples, and `ApiContractTest` (which also pins the endpoint count at 22) must be
   updated. Creating a resource returns `201` with a `Location` header.
10. **Claim documents have limits in three places that must agree:** the backend (`app.documents.*` and `spring.servlet.multipart.*`:
    5 files, 5 MB each, PDF/JPG/PNG), the UI's copy (`frontend/src/app/core/documents.ts`) and nginx (`client_max_body_size` in
    `frontend/nginx.conf`). Change all three together; the backend is the one that decides, and it checks the file's real type from its
    first bytes, never its name.
11. **The `ClaimStatusChanged` event is a contract between the backend and the notification service.** Only add fields, never rename or remove one
    (`ClaimAuditRecorderTest` pins the names the service reads). The event carries the claimant's email and name, which are personal data: do not add
    fields a consumer does not need, never log an address unmasked (`Masking.email` in the service), and keep the officer's notes out of the event.
    A change to the event changes `notification-service/` (`ClaimStatusChanged`, `EventParser`, its tests) and `ARCHITECTURE.md` section 7.3 in the same commit.
