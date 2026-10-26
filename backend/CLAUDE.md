# CLAUDE.md

## Project Context

This folder (`backend/`) contains a Claims Management System built as a monolithic Spring Boot application. The Angular UI that consumes
its API is in `../frontend` (see `frontend/CLAUDE.md`); the shared README, ARCHITECTURE and AI journal are in the repository root.

Purpose:

- Submit insurance claims.
- Assign claims to officers.
- Review and process claims.
- Approve, reject, or settle claims.
- Provide management dashboards.
- Enforce workflow and role-based security.

This repository is intended to demonstrate clean architecture, business-rule enforcement, security, testing, and maintainable design.

---

## Technology Stack

- Java 21
- Spring Boot 3.3
- Spring Web
- Spring Data JPA
- Spring Security
- JWT Authentication (jjwt 0.12)
- H2 in-memory (the database in use; the PostgreSQL datasource is only a commented example, with no driver)
- MapStruct
- Lombok
- OpenAPI / Swagger
- JUnit 5
- Mockito
- JaCoCo

(See "Current state vs this file" at the bottom for where the repository differs from this list.)

---

## Architecture

Architecture style: Modular Monolith.

Do NOT recommend microservices unless explicitly requested.

Current package structure:

```
com.claims
 ├── controller
 ├── service
 ├── repository
 ├── entity
 ├── dto
 ├── mapper
 ├── security
 ├── exception
 ├── validation
 ├── config
 └── util
```

Responsibilities:

- Controllers expose REST APIs only.
- Services contain business logic and transaction boundaries.
- Repositories handle persistence.
- DTOs define API contracts.
- Entities represent database models.
- Security package contains JWT authentication and authorization logic.

---

## Coding Standards

### Dependency Injection

Use constructor injection only.

Never introduce:

- Field injection (`@Autowired` on fields)
- Service locators
- Static service access

### DTOs

Prefer Java records for request and response DTOs.

### Mapping

Use MapStruct for entity-to-DTO mapping.

Avoid manual mapping when a MapStruct mapper already exists.

### Validation

Use Bean Validation annotations.

Business rules belong in the service layer.

Never rely solely on controller validation.

### Error Handling

Use existing exception hierarchy.

Prefer:

- BusinessException
- ResourceNotFoundException
- ValidationException

Maintain RFC 7807 problem responses.

---

## Security Rules

Authentication uses JWT.

Role names include:

- ROLE_CLAIMANT
- ROLE_OFFICER
- ROLE_MANAGER

Use method-level authorization where appropriate.

Do not bypass existing authorization checks.

Claim visibility rules:

- Claimants may only access their own claims.
- Officers may access claims assigned to them.
- Managers have broader operational access.

---

## Claim Workflow

Valid statuses:

- SUBMITTED
- UNDER_REVIEW
- MORE_INFO_REQUIRED
- APPROVED
- REJECTED
- SETTLED

Workflow rules are centralized in:

`ClaimStatus.allowedTransitions()`

Do not duplicate transition logic in controllers.

Invalid transitions must result in BusinessException.

---

## Business Rules

### Claim Submission

Required:

- policyNumber
- claimantName
- amountClaimed > 0
- incidentDate not in future
- description length >= 20

### Assignment

- A claim must be assigned before review.
- Rejected claims cannot be reassigned.
- Settled claims cannot be reassigned.

### Review

Only assigned officers or managers may process claims.

### Approval/Rejection

Approval and rejection must populate:

- resolvedAt

This data is used by dashboards and reporting.

### Settlement

Only approved claims can be settled.

---

## API Guidelines

Follow existing REST patterns.

Do not rename existing endpoints unless explicitly requested.

Preserve backward compatibility whenever possible.

If a breaking API change is required:

1. Update tests.
2. Update OpenAPI documentation.
3. Update the root README, and `frontend/` (its API layer, models and tests) if the changed endpoint is one the UI calls.
4. Update Postman collection.

---

## Database Guidelines

Development:

- H2
- ddl-auto=update

Production recommendations:

- PostgreSQL
- Flyway or Liquibase migrations

Do not introduce database-specific features unless requested.

---

## Testing Requirements

Before considering work complete:

```
mvn clean verify
```

---

## Current state vs this file

Facts about the repository as of 2026-10-14, so that Claude does not assume things that are not there. Review each
item and either change the code to match this file or change this file to match the code.

| This file says | What the repository actually has |
|---|---|
| Spring Boot 3.3 | Spring Boot **3.4.5** (`pom.xml` parent) |
| Lombok, MapStruct, JaCoCo, PostgreSQL in the stack | **Not in `pom.xml`.** Mapping is a manual static `ClaimMapper`; getters/constructors are hand-written; no coverage plugin; no PostgreSQL driver (a commented example exists in `application.properties`). |
| Use MapStruct for mapping | The existing mapper is manual (`ClaimMapper.toResponse`). Follow it; do not add MapStruct unless asked. |
| Role names are `ROLE_CLAIMANT` etc. | Enum values are `CLAIMANT`, `OFFICER`, `MANAGER`; authorisation uses `hasRole('CLAIMANT')`. Spring adds the `ROLE_` prefix. |
| Rules live in `ClaimStatus.allowedTransitions()` | `ClaimStatus` is a plain enum. The transition table is the `TRANSITIONS` map in **`ClaimService`**. |
| Approval and rejection populate `resolvedAt` | **No `resolvedAt` field exists.** Dashboards count claims by status instead. |
| A claim must be assigned before review | Enforced for **officers** (only the assigned officer may act). **Managers** can review an unassigned claim. |
| Rejected and settled claims cannot be reassigned | **Not enforced** in `ClaimService.assign`. |
| Officers may access claims assigned to them | Also true for **unassigned** claims, so they can read the queue (`GET /api/claims/queue`) and pick them up. Other officers' claims return 404. |
| Only the API is public: register, login, Swagger | The H2 console is **off by default** (`H2_CONSOLE_ENABLED`, `H2_CONSOLE_ALLOW_OTHERS` in `application.properties`). Only when it is on does `SecurityConfig` permit **`/h2-console/**` without a token** and set `X-Frame-Options: sameOrigin`; `ApiContractTest` checks it is unreachable otherwise. |
| The whole API is reachable from any front end | **No CORS configuration.** A browser on another origin is blocked; the UI uses the Angular dev proxy (or nginx in Docker). |
| (nothing about API documentation) | Controllers and DTOs carry OpenAPI annotations (`@Tag`, `@Operation`, `@ApiResponse`, `@Schema`). **Every endpoint needs a summary**; `ApiContractTest` fails otherwise and pins the endpoint count at 22. `POST /api/claims` returns `201` with a `Location` header. |
| (nothing about coverage) | JaCoCo 0.8.15 runs with `mvn test` and writes `target/site/jacoco/index.html` (97.1% of lines, 118 tests, on 2026-10-26). It is a report, not a build gate. |

## Features added after this file's rules were written

- Claimant answer to an information request: `PUT /api/claims/{id}/provide-info`.
- Officer queue and pick-up: `GET /api/claims/queue`, `PUT /api/claims/{id}/pickup`; `?status=` filter on `GET /api/claims`.
- Audit trail: `claim_status_history`, `GET /api/claims/{id}/history`. Every status change must go through
  `ClaimAuditRecorder.record(...)` inside the same transaction.
- Events: `ClaimStatusChanged` is written to the `outbox_events` table in the same transaction; `OutboxPublisher` relays it
  through `EventPublisher` (log by default, Kafka when `app.events.publisher=kafka`). Never publish to a broker directly
  from a service. The event also carries `claimantEmail` and `claimantName` (from the claim, not the actor) for the separate
  `notification-service/`, which emails the claimant: treat the event as a contract (add fields only; `ClaimAuditRecorderTest` pins the names)
  and as personal data (no officer notes, no extra fields).
- `claimType` (MOTOR/PROPERTY) and `market` (2-letter code) are required on claim submission.
- Optional claim documents: `POST|GET /api/claims/{id}/documents`, `GET .../documents/{documentId}` (`ClaimDocumentController`,
  `ClaimDocumentService`, entity `ClaimDocument`, table `claim_documents`, bytes in a `@Lob`). Rules, all in the service: only the owning
  claimant may attach, and only while the claim is not REJECTED or SETTLED; at most 5 files of 5 MB (`app.documents.max-files`,
  `app.documents.max-bytes`); PDF, JPG or PNG **decided from the file's first bytes** and the extension must agree; the name is stripped of
  directories and control characters. Who may see a document is exactly who may see the claim (`ClaimService.viewableClaim`, which answers 404
  for a claim the caller may not see). Downloads are `attachment` with `nosniff` and `no-store`. Do not trust `getContentType()` from the
  client, and do not widen the accepted types without adding the signature for each.

See `../ARCHITECTURE.md` (part B) for the reasoning.
