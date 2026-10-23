# CLAUDE.md — Claims Platform (repository root)

Guidance for Claude Code (and any human) working in this repository. This is an index; the detailed rules live next to the code.

## What this is

One repository, two applications, one set of documents:

| Path | What | Rules for working in it |
|---|---|---|
| `backend/` | Spring Boot 3.4 / Java 21 REST API (owner of the API contract) | [backend/CLAUDE.md](backend/CLAUDE.md) |
| `frontend/` | Angular 22 single-page app for claimants, officers and managers | [frontend/CLAUDE.md](frontend/CLAUDE.md) |
| `README.md` | How to run it, accounts, API summary | keep it the single entry point |
| `ARCHITECTURE.md` | Backend (part B), frontend (part C), integration (part D), limitations (part E) | update it when a design decision changes |
| `AI_JOURNAL.md` | How AI was used: backend Parts A–B, frontend Part C (F-numbers), open questions Part D, limitations E, open items F | see below |
| `docs/` | `E2E-VALIDATION-FLOW.txt` (manual test script), `SWAGGER-TEST-GUIDE.txt` | keep the numbers in the E2E script true when behaviour changes |
| `docker-compose.yml`, `DOCKER.md`, `*/Dockerfile`, `frontend/nginx.conf` | Containers: Kafka + backend + frontend | keep `DOCKER.md` and the compose file in step; the topic name appears in compose twice and must match `EVENTS_TOPIC` |

## Rules that apply to everything

1. **The backend owns the contract.** UI work never edits `backend/`; if the backend needs a change, record it in `AI_JOURNAL.md` Part D
   and ask the author. A backend change that alters an endpoint also changes `frontend/` (API service, models, tests) in the same commit.
2. **Run commands from the folder they belong to:** `mvn ...` in `backend/`, `npm ...` in `frontend/`. Start the backend first; the UI
   reaches it through the dev proxy (`frontend/proxy.conf.json`), so always browse `http://localhost:4200`.
3. **Tests are part of the change.** Backend: `mvn test` (82 tests). Frontend: `npm test -- --no-watch` (216 tests) and `npm run build`.
   A behaviour change needs a test change in the same commit.
4. **Update `AI_JOURNAL.md` in the same change as any non-trivial AI-driven step.** Be honest: record what was corrected or rejected,
   not just what worked, and what was *not* verified. Leave `Decision` lines for the author (`Pending author review`); never invent
   the author's decisions or fill in history you did not witness.
5. **Commit only when asked.** Small, conventional commits (`feat(api): …`, `feat(ui): …`, `test: …`, `docs: …`), one concern each.
6. **Ask before installing or downloading anything. Never commit tokens, passwords or build output** (`target/`, `dist/`,
   `node_modules/`, `.build-cache/` are ignored by the root `.gitignore`).
7. The database is PostgreSQL and **keeps its data** between runs; reset it (`DOCKER.md` part 6) before running `docs/E2E-VALIDATION-FLOW.txt`.
   Backend tests use in-memory H2 in PostgreSQL mode (`backend/src/test/resources/config`), so `mvn test` needs no database.
   Connection settings are `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (defaults: `localhost:5432/claimsdb`, `claims`, `change-me`, local use only).
