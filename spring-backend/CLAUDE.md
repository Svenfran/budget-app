# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Spring Boot 2.7.3 / Java 17 REST backend for "Budget App" (a.k.a. Divvy) — a shared-expense / shopping-list app. Groups of users record `Cart` entries (expenses), split costs, settle balances, and share `ShoppingList`s. This directory (`spring-backend`) is one module of the larger `budget-app` repo (sibling `ionic-frontend` is the client). Cross-module architecture and working rules are in the root `CLAUDE.md`.

Base package: `com.github.svenfran.budgetapp.budgetappbackend` (note: the hyphenated Maven coordinates `com.github.svenfran.budget-app` are invalid as a Java package, hence the divergence).

## Commands

Use the Maven wrapper (`./mvnw` / `mvnw.cmd`). Requires a reachable Postgres (see Environment below) for anything that boots the context.

```bash
./mvnw clean package              # build jar (runs tests)
./mvnw clean package -DskipTests  # build without tests (what the Dockerfile does)
./mvnw spring-boot:run            # run locally; the plugin forces the 'dev' profile (pom.xml)

./mvnw test                       # run all tests (JUnit + Spock, via surefire)
./mvnw test -Dtest=CartServiceSpec        # single Spock/JUnit class
./mvnw test -Dtest=CartServiceSpec#'method name'   # single Spock feature method
```

Surefire is configured to pick up `*Test`, `*IT`, and `*Spec` classes. Spock/Groovy sources are compiled by the `gmavenplus-plugin`.

## Environment & profiles

- Secrets/config come from environment variables. At startup `BudgetAppApplication.main` loads a `.env` file (via `dotenv-java`, `ignoreIfMissing`) into system properties before Spring starts — so a local `.env` in this directory works the same as real env vars. `.env` is gitignored (never commit it); it holds real secrets (DB, JWT, Brevo), so do not echo its contents into output or commits.
- Properties: there is **no** `application-dev.properties`. `application.properties` is the base file loaded for *every* profile and holds the dev values; `application-prod.properties` only overrides selected keys. Anything prod doesn't override is inherited from the base file, so when adding a dev-only value to `application.properties`, add a prod override too. Prod explicitly sets its own CORS origins, `websocket.url`, `server.address`, `show-sql=false` and quieter log levels (INFO / Security WARN).
- Profiles:
  - `dev` (forced by `spring-boot-maven-plugin` for `spring-boot:run`): port **9090** on `0.0.0.0`, Liquibase context `dev` (loads demo data). Reads `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`.
  - `prod` (forced by the Dockerfile entrypoint; deployed on Railway): port `${PORT:8080}` (Railway injects `PORT`), Liquibase context `prod` with `db.changelog-master-prod.xml`. Reads `PGHOST`/`PGPORT`/`PGDATABASE`/`PGUSER`/`PGPASSWORD`.
  - `test` (`src/test/resources/application-test.properties`): Liquibase context `test`, dummy Brevo sender; used by integration tests (see below).
- `ddl-auto=none` — schema is owned entirely by Liquibase, never Hibernate.

### Local Postgres

`docker-compose.yml` spins up the local **dev** Postgres the running backend talks to (`docker compose up -d`): host port **5442**, db `postgres`. User/password are taken from `DB_USERNAME`/`DB_PASSWORD` in the (gitignored) `.env`, which Compose reads automatically — never hard-code credentials in the compose file (GitGuardian flags them on PRs). Host port 5442 (mapped to the container's internal 5432) avoids clashing with any Postgres already on host port 5432.

Integration tests start their own Postgres programmatically via `TestContainerEnv`; no compose file is needed for them. With Docker Desktop 29+, Testcontainers 1.19.1 fails with "Could not find a valid Docker environment" (HTTP 400, API version too old) — run tests with `DOCKER_API_VERSION=1.44` set, or upgrade Testcontainers.

## Architecture

Standard layered Spring MVC: **Controller → Service → Repository (Spring Data JPA) → Postgres**, with DTOs at the boundary.

- **Controllers** (`controller/`) are thin; all under `@RequestMapping("/api")` except `AuthenticationController` (`/api/auth`). They delegate to services and return DTOs.
- **Services** (`service/`) hold all business logic. Two service objects are pervasive collaborators — understand them before touching any service:
  - `DataLoaderService` — central loader. `getAuthenticatedUser()` pulls the current user from the `SecurityContext`; `loadGroup`/`loadCart`/etc. fetch entities or throw the appropriate `*NotFoundException`. Services start almost every method by calling these instead of using repositories directly.
  - `VerificationService` — authorization checks (`verifyIsPartOfGroup`, owner checks, etc.). The standard method shape is: load user → load entity → `verificationService.verify…` → do work. Preserve this ordering when adding endpoints.
- **DTOs** (`dto/`) convert from entities, often via a constructor `new XxxDto(entity)` or a dedicated mapper in `service/mapper/`. Entities are never returned from controllers.
- **Entities** (`entity/`): core domain is `User`, `Group`, `Cart`, `Category`, `ShoppingList`, `ShoppingItem`, plus `GroupMembershipHistory`, `CartTemplate`, and `Token`.
- **Exceptions** (`exceptions/`): one checked exception per domain rule. They are translated to HTTP responses centrally in `controller/RestResponseEntityExceptionHandler`. When adding a business rule, add a typed exception and map it there rather than throwing generic errors.

### Cross-cutting concerns

- **Auth**: stateless JWT. `JwtAuthenticationFilter` (registered in `SecurityConfiguration`) validates the `Authorization` bearer token per request and populates the `SecurityContext`; `JwtService` issues/parses tokens; `Token` entities are persisted (used for logout/revocation). Public paths: `/api/auth/**`, `/api/userprofile/password-reset`, `/ws/**`, `/wss/**`. CORS allowed origins come from the `app.cors.allowed-origins` property (comma-separated; dev: LAN IPs + localhost:8100 for the Ionic dev server; prod: only `http://localhost` and `https://localhost`, the WebView origins of the Capacitor Android app), injected into `CorsConfig`, `SecurityConfiguration` and `WebSocketConfig` — add new origins there, not in code. `helper/OriginLoggingFilter` logs the `Origin` header of incoming requests.
- **WebSocket / notifications**: STOMP over SockJS (`WebSocketConfig`), endpoint from `websocket.url` — `/ws` in all profiles (`/wss` is still permitted in `SecurityConfiguration` but unused). `NotificationService` pushes live updates to clients; the broker prefixes are `/notification` and `/user`, app prefix `/app`.
- **Scheduling** (`@EnableScheduling`): cron expressions come from properties so dev runs them aggressively and prod sparingly.
  - `CartService` recurring-cart job (`task.recurringCart.schedule`) — materializes new `Cart`s from active `CartTemplate`s. Recurrence is modeled as a `CartTemplate` (with `RecurrenceType`, `nextExecutionDate`, `active`); a one-off cart has no template (`RecurrenceType.NONE`). See `../docs/sketches` in the repo root.
  - `AuthenticationService` token cleanup (`task.clearToken.schedule`).
  - `NotificationService` has a `@Scheduled(fixedRate = 10000)` sweep.
- **i18n**: `messages.properties` (German, default), `messages_en.properties`, `messages_es.properties`. Locale is resolved from the `Accept-Language` header (`AppLanguageConfig` → `AcceptHeaderLocaleResolver`, default `Locale.GERMAN`, no system-locale fallback). Use the `Translator` component (`translate(key, args...)`) for any user-facing string — services pass message *keys* (e.g. `"categories.default.settlement"`) rather than literals. Excel export (`ExcelWriter`) is also localized.
- **Email**: Brevo (SendinBlue) via `BrevoMailSenderService` for verification / password reset, configured by `brevo.*` properties.
- **Excel export**: `helper/ExcelWriter` uses Apache POI to stream `.xlsx` spending overviews directly to the `HttpServletResponse`.
- **Dates**: use `helper/DateUtils` (`toLocalDate`/`toDate`, `NO_END_DATE` = 2999-12-31 as the "open-ended" sentinel) instead of ad-hoc `Date`↔`LocalDate` conversions.
- **Validation**: custom `@ValidEmail` constraint (`validator/`) on auth/user DTOs. Enums such as `RecurrenceType` and `TokenType` live in `constants/`.
- **Health**: Spring Actuator exposes only `/actuator/health`.

### Database / Liquibase

Schema and seed data are managed by Liquibase, **not** JPA. Changelogs live under `src/main/resources/db/changelog/`:
- `db.changelog-master.xml` (dev/test) and `db.changelog-master-prod.xml` (prod) are the two entry points referenced by the respective profiles.
- Each change is a separate file in `release-1.x/` (`release-2.x/` exists but is still empty), included with explicit `context` attributes. In `db.changelog-master.xml`, schema changesets use `context="dev,test,prod"` and seed/demo-data changesets use `context="dev,!test"` so they load in dev but never in tests. `db.changelog-master-prod.xml` includes everything with `context="prod"` (incl. prod-specific data loads like `carts_prod.csv`).
- Seed data is loaded from CSV files in `src/main/resources/db/data/`.
- To change the schema: add a new timestamped changeset file, then `<include>` it in **both** master changelogs with appropriate contexts. Never edit an already-applied changeset.

## Testing

Two test stacks coexist under `src/test/`:
- **Spock/Groovy** (`src/test/groovy/...`) — preferred for new tests. Unit specs in `service/unit/`, integration specs in `service/integration/`.
- **JUnit/Java** (`src/test/java/...`) — older repository/service tests.

Integration tests use **Testcontainers** with a real Postgres. Spock integration specs extend `service/container/TestContainerEnv` (`@ActiveProfiles("test")`, `@SpringBootTest` random port, reusable `postgres:14-alpine` container); the JUnit equivalent is `container/TestContainerEnv.java` / `BaseClass.java`. A Docker daemon must be running for these. Shared test fixtures: `TestDataFactory.groovy` and `CreateDataService.java`.
