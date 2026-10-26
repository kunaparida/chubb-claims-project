# Running the Claims Platform in Docker

This guide takes you from a machine with no Docker to the whole system running: **Kafka + backend + notification service + a local inbox + frontend**, started by one command.

| Container | What it is | Where you reach it |
|---|---|---|
| `frontend` | Angular app served by nginx; nginx forwards `/api/...` to the backend | **http://localhost:4200** |
| `backend` | Spring Boot API (in-memory H2 database; restarted automatically unless stopped) | http://localhost:8080 · Swagger: http://localhost:8080/swagger-ui/index.html · H2 console only if enabled (5.5) |
| `kafka` | Single-node Apache Kafka 3.9 (KRaft mode, no ZooKeeper) | `kafka:9092` inside Docker, `localhost:9094` from your machine |
| `kafka-init` | One-shot job that creates the topic `claims.claim-status-changed` and its dead-letter topic `claims.claim-status-changed.dlt`, then exits | not a server |
| `notification-service` | Reads the status-change events from Kafka and emails the claimant (health only: `http://localhost:8081/actuator/health` from inside the container) | no published port |
| `mailpit` | A local inbox: catches every email the notification service sends. Nothing reaches a real address | **http://localhost:8025** (SMTP on `mailpit:1025` inside Docker) |

Files that make this work: `docker-compose.yml` (root), `backend/Dockerfile`, `notification-service/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, and a `.dockerignore` in each app folder.

> **Status of these files (read this first).** They were written and reviewed against the project's code and Spring Boot / Angular / Kafka
> conventions, but **Docker was not available to the AI that wrote them, so it never built an image or started a container.** The
> author has since edited the compose file (a restart policy on the backend, and H2 console settings that are now off by default), but no
> run result is recorded anywhere. The backend, the notification service and the frontend themselves were built and tested (118, 76 and 312 tests). The Kafka
> path was run against a real broker only inside a JVM (the notification service's tests, and one live run of the real applications on 2026-10-26), never against
> this stack's broker, and the `mailpit` and `notification-service` containers have never been started. Part 5 below is how you prove it for real; if a step fails, [part 8](#8-troubleshooting)
> and `docker compose logs` will say why, and please record the result in `AI_JOURNAL.md` (entry F5).

## Contents

1. [Install Docker (Windows)](#1-install-docker-windows)
2. [Check the install](#2-check-the-install)
3. [Free the ports](#3-free-the-ports)
4. [Build and start everything](#4-build-and-start-everything)
5. [Prove it works, including Kafka](#5-prove-it-works-including-kafka)
6. [Everyday commands](#6-everyday-commands)
7. [Kafka configuration check](#7-kafka-configuration-check)
8. [Troubleshooting](#8-troubleshooting)
9. [Other ways to run](#9-other-ways-to-run)

---

## 1. Install Docker (Windows)

**You need:** Windows 10/11 64-bit, virtualization enabled in the BIOS/UEFI (Task Manager → Performance → CPU shows "Virtualization: Enabled"),
about 4 GB of free RAM for Docker and 10 GB of disk.

**1.1 Install WSL 2** (the Linux layer Docker Desktop uses). Open **PowerShell as Administrator**:

```powershell
wsl --install
```

Restart the computer when it asks. (If WSL is already installed, `wsl --update` is enough.)

**1.2 Install Docker Desktop.** Either download the installer from <https://www.docker.com/products/docker-desktop/> and run it with the
default option "Use WSL 2 instead of Hyper-V", or from a normal PowerShell:

```powershell
winget install -e --id Docker.DockerDesktop
```

Restart again if the installer asks.

**1.3 Start Docker Desktop** from the Start menu, accept the terms, and wait until the whale icon says **Engine running**.

> **Licence note.** Docker Desktop is free for personal use, education and small businesses; larger companies need a paid subscription.
> If that is a problem on a company laptop, Rancher Desktop or Podman Desktop can run the same `docker-compose.yml`; the commands below
> are then the same or very close.

**1.4 (Optional) limit Docker's memory.** WSL 2 may take up to half of your RAM. To cap it, create `%UserProfile%\.wslconfig` with:

```ini
[wsl2]
memory=4GB
processors=2
```

then run `wsl --shutdown` and start Docker Desktop again.

## 2. Check the install

Open a **new** terminal (so it sees Docker on the PATH) and run these one at a time:

```bash
docker --version
```

```bash
docker compose version
```

```bash
docker run --rm hello-world
```

The last one downloads a tiny image and prints "Hello from Docker!". If it does, Docker works. (Docker Compose v2 is included with Docker
Desktop; the command is `docker compose` with a space, not `docker-compose`.)

## 3. Free the ports

The containers need these ports on your machine: **4200**, **8025**, **8080**, **9094**. Stop anything else using them first, especially a backend
started from IntelliJ (port 8080) or `npm start` (port 4200). To see what is using a port:

```powershell
Get-NetTCPConnection -State Listen -LocalPort 4200,8025,8080,9094 -ErrorAction SilentlyContinue | Select-Object LocalPort, OwningProcess
```

No output means all four are free.

## 4. Build and start everything

From the project root (the folder that contains `docker-compose.yml`):

```bash
cd C:\project\chubb-claims-project
```

```bash
docker compose up --build -d
```

What happens, in order: Docker builds the backend image (Maven compiles and packages the jar), builds the frontend image (`npm ci`, then the
Angular production build), builds the notification service image, pulls the Kafka and Mailpit images, starts Kafka and waits until it is healthy,
runs `kafka-init` to create the two topics, starts the backend and waits until it is healthy (about a minute), starts the notification service
and Mailpit, and finally starts the frontend.
**The first run downloads a lot and can take several minutes**; later runs reuse the cache and are much faster.

Check the result:

```bash
docker compose ps
```

You want: `claims-kafka` **healthy**, `claims-kafka-init` **exited (0)**, `claims-backend` **healthy**, `claims-notification` **healthy**, `claims-mailpit` **running**, `claims-frontend` **running**.

Watch the backend start (press `Ctrl+C` to stop watching; the containers keep running):

```bash
docker compose logs -f backend
```

Then open **http://localhost:4200** and sign in:

| Role | Email | Password |
|---|---|---|
| Officer | `officer@claims.local` | `ChangeMe123!` |
| Manager | `manager@claims.local` | `ChangeMe123!` |
| Claimant | choose **Create an account** | your own (8-72 characters) |

For a full click-through with expected numbers, follow `docs/E2E-VALIDATION-FLOW.txt` (it assumes a fresh database, which you have right now).

## 5. Prove it works, including Kafka

**5.1 The topic exists.**

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
```

Expected: `claims.claim-status-changed` and `claims.claim-status-changed.dlt` (plus Kafka's internal topics once something has used them).

**5.2 Watch events arrive.** In a **second terminal**, start a consumer and leave it open:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic claims.claim-status-changed --from-beginning --property print.key=true
```

**5.3 Cause events from the UI.** In the browser:

1. Create a claimant account and **Report an incident**.
2. Within about 5 seconds (the outbox poll interval) the consumer prints a line like
   `1   {"eventId":"...","claimId":1,"claimNumber":"CLM-...","claimType":"MOTOR","market":"SG","amountClaimed":1250.00,"fromStatus":null,"toStatus":"SUBMITTED","changedBy":"...","occurredAt":"...","claimantEmail":"...","claimantName":"..."}`
   The first column is the Kafka key, which is the claim id.
3. Sign in as `officer@claims.local`, pick the claim up (no event: picking up does not change the status), click **Start review**, then approve or settle it.
   Each status change prints one more event for the same key, in order.

If events appear, the whole chain works: status change → database transaction (history + outbox row) → relay → Kafka.

**5.3A Read the email the claimant gets.** Open **http://localhost:8025** (Mailpit). For each status change you made in 5.3 there is one email to the
claimant's address, in the same order: "We have received your claim ...", "Your claim ... is now under review", and so on. Open one: it has a text and an HTML
view, the amount with its market, and a "View your claim" link to `http://localhost:4200/claims/<id>`. Check the service too:

```bash
docker compose logs notification-service
```

Each email is one line, `Told j***@example.com that claim CLM-... moved ... -> ...`; the address is masked on purpose. Then, to prove a restart does not repeat emails:

```bash
docker compose restart notification-service
```

Wait a minute: Mailpit must still show the same number of emails.

**5.3B Prove a failing mail server does not lose the event** (optional). Stop the inbox, change a claim's status, start the inbox again:

```bash
docker compose stop mailpit
```

Do one action in the UI. `docker compose logs -f notification-service` now shows `Delivery attempt 1 failed ...`, then 2 and 3, one to four seconds apart. If you start Mailpit
again quickly enough (`docker compose start mailpit`), a later attempt succeeds and the email arrives. If you wait for all attempts to fail (about ten seconds), the record is
moved to the dead-letter topic; read it with:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic claims.claim-status-changed.dlt --from-beginning --property print.headers=true
```

The headers (`kafka_dlt-exception-message`, `kafka_dlt-original-topic`) say why. Nothing replays the dead-letter topic automatically (see `ARCHITECTURE.md` part E).

**5.4 Prove the outbox survives a broker outage** (optional, shows why the outbox exists):

```bash
docker compose stop kafka
```

Do one action in the UI (for example, start a review). The action succeeds. Now look at the backend log:

```bash
docker compose logs --tail 20 backend
```

After a while (the Kafka client can wait up to about a minute for the broker, see [part 7](#7-kafka-configuration-check)) you will see
`Publishing outbox event ... failed, will retry`. Then bring Kafka back:

```bash
docker compose start kafka
```

Within one to two poll intervals the consumer from 5.2 (restart it if it exited) shows the event that was waiting. Nothing was lost.

**5.5 Cross-check with the database console** (optional). The H2 console is **off by default** because it needs no sign-in. Start the stack
with it switched on (the compose file then also lets your browser, outside the container, reach it):

```powershell
$env:H2_CONSOLE_ENABLED = "true"; docker compose up -d
```

Then open http://localhost:8080/h2-console, JDBC URL `jdbc:h2:mem:claimsdb`, user `sa`, empty password, and run `SELECT * FROM OUTBOX_EVENTS;`.
Published rows have a publish timestamp; a row without one has not reached Kafka yet. Switch it off again afterwards
(`Remove-Item Env:H2_CONSOLE_ENABLED; docker compose up -d`) and use it only on your own machine.

## 6. Everyday commands

| Goal | Command |
|---|---|
| Start (build if needed) in the background | `docker compose up --build -d` |
| See what is running | `docker compose ps` |
| Follow one service's log | `docker compose logs -f backend` (or `notification-service`, `frontend`, `kafka`) |
| Stop, keep containers (fast restart) | `docker compose stop` |
| Start again after `stop` | `docker compose start` |
| Stop and remove containers and network | `docker compose down` |
| Also remove the images built here | `docker compose down --rmi local` |
| Rebuild after changing backend code | `docker compose up --build -d backend` |
| Rebuild after changing frontend code | `docker compose up --build -d frontend` |
| Rebuild after changing the notification service | `docker compose up --build -d notification-service` |
| Send real email instead of using Mailpit | Set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH=true`, `MAIL_STARTTLS=true` and `MAIL_FROM` in your shell (or a git-ignored `.env`) before `docker compose up -d`. Never put the password in `docker-compose.yml` |
| Rebuild with the unit tests running | `docker compose build --build-arg SKIP_TESTS=false backend` |
| Shell inside a container | `docker compose exec backend sh` |
| Disk used by Docker | `docker system df` |

**Data to expect:** the backend database is in memory, so **every backend restart erases all accounts and claims** (the officer and manager
are re-created). Kafka has no volume either, so `docker compose down` erases the topic and its events; `stop`/`start` keeps them.

**Restart policy:** the backend has `restart: unless-stopped`, so Docker starts it again if it crashes (and after a Docker restart), which also
wipes its in-memory data. If the backend keeps restarting, `docker compose logs backend` shows each start-up and the error behind it.

## 7. Kafka configuration check

What was checked in the code (`application.properties`, `KafkaEventPublisher`, `OutboxPublisher`, `pom.xml`) and what this setup does about it:

| Item | Finding | What the Docker setup does |
|---|---|---|
| Publisher switch | `app.events.publisher=${EVENTS_PUBLISHER:log}`: defaults to logging, Kafka only when set to `kafka` | Compose sets `EVENTS_PUBLISHER=kafka` (override with `EVENTS_PUBLISHER=log docker compose up -d`) |
| Broker address | `spring.kafka.bootstrap-servers` is **commented out** in `application.properties`; the `spring-kafka` dependency is present | Passed as the environment variable `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092` (Spring Boot maps it to the property), so no source change |
| Topic name | `app.events.topic=${EVENTS_TOPIC:claims.claim-status-changed}` | Same name set explicitly in compose and in `kafka-init` |
| Topic creation | Neither application creates a topic (no `NewTopic` bean) | `kafka-init` creates `claims.claim-status-changed` (3 partitions) and its dead-letter topic `.dlt` (1 partition), replication factor 1. Broker auto-creation is **disabled**, so a topic-name mismatch fails visibly instead of silently creating a stray topic. If you change `EVENTS_TOPIC` or `DEAD_LETTER_TOPIC`, change `kafka-init` too |
| Serialization | No producer settings; Spring Boot defaults to String key and String value, matching `KafkaTemplate<String, String>` | Nothing to do. Key = claim id, so one claim's events stay ordered in one partition; value = the JSON event |
| Delivery | The relay waits up to 10 s for the broker acknowledgement and marks the row published only afterwards; at-least-once | Consumers must de-duplicate on `eventId` (documented in `ARCHITECTURE.md` part B.3) |
| Relay scheduling | `@EnableScheduling` is present (`SchedulingConfig`); poll every `OUTBOX_POLL_MS` (5 s), 50 rows per run | Compose sets `OUTBOX_POLL_MS=5000` |
| **Broker-down behaviour** | By Kafka's default `max.block.ms` (60 s), `send()` can wait up to a minute for broker metadata *before* the 10 s acknowledgement timeout starts, holding the relay thread and its database transaction meanwhile | **Not changed** (the Kafka settings are as they were). Recommended: `spring.kafka.producer.properties.max.block.ms=10000` in `application.properties` |
| Consumers | The notification service (group `claims-notification-service`, offsets committed after each email, retries then dead-letter) | It runs in compose; use the console consumer in 5.2 to observe the same events |
| Broker setup | Single node, replication factor 1, PLAINTEXT, no data volume | Fine for local and demo use; **not** production configuration |
| Test coverage | The publisher is unit-tested with a mock; publisher and consumer have run against a real broker started inside a JVM (tests, and a live run on 2026-10-26), never against this broker | This compose file is the first real test of the containers: run part 5 and record the outcome |

## 8. Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `error during connect` / `cannot find the file specified` / "docker daemon is not running" | Docker Desktop is not started. Start it and wait for "Engine running" |
| Docker Desktop says WSL 2 or virtualization is missing | Run `wsl --install` as administrator and restart; enable virtualization in the BIOS |
| `Bind for 0.0.0.0:8080 failed: port is already allocated` | Something else uses the port. See [part 3](#3-free-the-ports) (usually the backend run from IntelliJ) |
| `docker compose up` fails with `service "kafka" is unhealthy` | `docker compose logs kafka` shows why. Often not enough memory: raise Docker's memory (1.4) and retry |
| `kafka-init` exits with an error | `docker compose logs kafka-init`. The broker was not ready or the topic command failed; run `docker compose up -d` again |
| Backend stays `starting` or `unhealthy` | `docker compose logs backend`. The first start takes about a minute; a stack trace usually names the property or class |
| http://localhost:4200 loads but sign-in shows "Cannot reach the server" | Backend is not healthy yet or crashed. `docker compose ps`, then `docker compose logs backend` |
| Frontend container exits at start with `host not found in upstream "backend"` | The backend container was not on the network; run `docker compose up -d` from the project root, not `docker run` |
| Build error `npm ci` ... `EBADENGINE` or `npm ERR! code ...` | The Node version in `frontend/Dockerfile` must satisfy Angular 22 (22.22.3+, 24.15+ or 26+). Check `docker run --rm node:24-slim node --version` |
| Build error `dist/claim-management-ui/browser: not found` | The Angular output path changed. Check `angular.json` and update the `COPY --from=build` line in `frontend/Dockerfile` |
| No events appear in the consumer | Check `docker compose logs backend` for `Publishing outbox event ... failed`; check `EVENTS_PUBLISHER` is `kafka` (`docker compose exec backend printenv EVENTS_PUBLISHER`) |
| Events appear in Kafka but no email in Mailpit | `docker compose logs notification-service`. `Delivery attempt ... failed` names the cause (mail server unreachable: is `mailpit` running and `MAIL_HOST` right?). Nothing logged at all: check the container is healthy and that `NOTIFICATIONS_MAIL_MODE` is not `log` (that mode only prints the email) |
| `notification-service` is unhealthy or restarts | `docker compose logs notification-service`. A message starting `notifications.mail.mode ...` means a wrong `NOTIFICATIONS_MAIL_MODE` or a missing `MAIL_HOST`; an error about an unknown topic means `kafka-init` has not created `claims.claim-status-changed` yet or the names differ |
| A record on `claims.claim-status-changed.dlt` | The service could not turn it into an email: the headers say why (`kafka_dlt-exception-message`). Fix the cause; nothing replays it automatically. The record holds the claimant's email address |
| Old claims never produce emails | Events published by a backend older than the notification service have no `claimantEmail`; they are dead-lettered. New status changes work |
| Attaching a document in the app fails with `413` and no message, although the file is under 5 MB | A proxy in front of the app is limiting request size. The bundled `frontend/nginx.conf` allows 7 MB (`client_max_body_size 7m`, for the backend's 5 MB file plus overhead); nginx's own default is 1 MB. If you put another gateway in front, raise its limit too. Not yet confirmed in a running container, like the rest of this stack |
| Slow first build / out of disk | `docker system df`; free space with `docker system prune` (it removes unused images and build cache; read the prompt before confirming) |
| Everything is gone after a restart | By design: in-memory database and no Kafka volume (part 6) |

## 9. Other ways to run

**Only Kafka in Docker, backend in IntelliJ, UI with `npm start`** (handy while developing):

```bash
docker compose up -d kafka kafka-init
```

In IntelliJ, add these environment variables to the backend run configuration, then run it:

```text
EVENTS_PUBLISHER=kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9094
```

Then, in `frontend/`, `npm start` and open http://localhost:4200 as usual.

**Everything in Docker but without Kafka** (events are only logged):

```powershell
$env:EVENTS_PUBLISHER = "log"; docker compose up --build -d
```

**Production notes.** Before any real deployment: set a unique `JWT_SECRET` (Base64, at least 32 bytes) and `BOOTSTRAP_PASSWORD` as environment
variables on the backend service, move from in-memory H2 to PostgreSQL, give Kafka a volume, three brokers and TLS/SASL, and serve the frontend
over HTTPS. See `ARCHITECTURE.md` part E for the full list.
