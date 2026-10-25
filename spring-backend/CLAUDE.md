# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Spring Boot 2.7.3 / Java 17 REST backend for "Budget App" (a.k.a. Divvy) — a shared-expense / shopping-list app. Groups of users record `Cart` entries (expenses), split costs, settle balances, and share `ShoppingList`s. This directory (`spring-backend`) is one module of the larger `budget-app` repo (sibling `ionic-frontend` is the client). Cross-module architecture and working rules are in the root `CLAUDE.md`.

Base package: `com.github.svenfran.budgetapp.budgetappbackend` (note: the hyphenated Maven coordinates `com.github.svenfran.budget-app` are invalid as a Java package, hence the divergence).

## Commands

Use the Maven wrapper (`./mvnw` / `mvnw.cmd`). `spring-boot:run` needs a reachable Postgres (see Local Postgres below); the tests start their own database and only need a running Docker daemon.

```bash
./mvnw clean package              # build jar (runs the unit specs only)
./mvnw clean package -DskipTests  # build without tests (what the Dockerfile does)
./mvnw spring-boot:run            # run locally; the plugin forces the 'dev' profile (pom.xml)

./mvnw test                       # unit specs (*Spec) via Surefire — fast, no Docker
./mvnw verify                     # + integration/API specs (*IntegrationSpec, *ApiSpec) via Failsafe — needs Docker; run before every PR

./mvnw test -Dtest=VerificationServiceSpec                                 # single unit spec
./mvnw test "-Dtest=VerificationServiceSpec#Falsches Passwort wird abgelehnt"   # single feature method
./mvnw verify -Dit.test=CartServiceIntegrationSpec -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false   # single integration/API spec
```

Surefire runs `**/*Spec.*` except `*IntegrationSpec`/`*ApiSpec`; Failsafe runs those two (both configured in `pom.xml`). `-Dtest` only affects Surefire, `-Dit.test` only Failsafe. Spock/Groovy sources are compiled by the `gmavenplus-plugin`.

## Environment & profiles

- Secrets/config come from environment variables. At startup `BudgetAppApplication.main` loads a `.env` file (via `dotenv-java`, `ignoreIfMissing`) into system properties before Spring starts — so a local `.env` in this directory works the same as real env vars. `.env` is gitignored (never commit it); it holds real secrets (DB, JWT, Brevo), so do not echo its contents into output or commits.
- Properties: there is **no** `application-dev.properties`. `application.properties` is the base file loaded for *every* profile and holds the dev values; `application-prod.properties` only overrides selected keys. Anything prod doesn't override is inherited from the base file, so when adding a dev-only value to `application.properties`, add a prod override too. Prod explicitly sets its own CORS origins, `websocket.url`, `server.address`, `show-sql=false` and quieter log levels (INFO / Security WARN).
- Profiles:
  - `dev` (forced by `spring-boot-maven-plugin` for `spring-boot:run`): port **9090** on `0.0.0.0`, Liquibase context `dev` (loads demo data). Reads `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`.
  - `prod` (forced by the Dockerfile entrypoint; deployed on Railway): port `${PORT:8080}` (Railway injects `PORT`), Liquibase context `prod` with `db.changelog-master-prod.xml`. Reads `PGHOST`/`PGPORT`/`PGDATABASE`/`PGUSER`/`PGPASSWORD`.
  - `test` (`src/test/resources/application-test.properties`): Liquibase context `test` (schema, no demo data), dummy Brevo sender, fixed dummy `secret_key` (tests never depend on a local `.env`), scheduled jobs off (`app.scheduling.enabled=false`), banner off and log-level overrides (format and library levels: `src/test/resources/logback-test.xml`, see Testing → Logging); used by integration and API specs (see Testing).
- `ddl-auto=none` — schema is owned entirely by Liquibase, never Hibernate.

### Local Postgres

`docker-compose.yml` spins up the local **dev** Postgres the running backend talks to (`docker compose up -d`): host port **5442**, db `postgres`. User/password are taken from `DB_USERNAME`/`DB_PASSWORD` in the (gitignored) `.env`, which Compose reads automatically — never hard-code credentials in the compose file (GitGuardian flags them on PRs). Host port 5442 (mapped to the container's internal 5432) avoids clashing with any Postgres already on host port 5432.

Integration and API specs don't use it: `testsupport/PostgresTestContainer` starts **one** Postgres container per test run via Testcontainers (image set there; no reuse, Ryuk removes it afterwards). No compose file and no extra environment variables are needed — just a running Docker daemon.

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

- **Auth**: stateless JWT. `JwtAuthenticationFilter` (registered in `SecurityConfiguration`) validates the `Authorization` bearer token per request and populates the `SecurityContext`; `JwtService` issues/parses tokens; `Token` entities are persisted (used for logout/revocation). Public paths: `/api/auth/**`, `/api/userprofile/password-reset`, `/ws/**`, `/wss/**`. CORS allowed origins come from the `app.cors.allowed-origins` property (comma-separated; dev: LAN IPs + localhost:8100 for the Ionic dev server; prod: only `http://localhost` and `https://localhost`, the WebView origins of the Capacitor Android app), injected into `SecurityConfiguration` (`corsConfigurationSource`, applies to all paths via the security filter chain) and `WebSocketConfig` — add new origins there, not in code. `helper/OriginLoggingFilter` logs the `Origin` header of incoming requests.
- **WebSocket / notifications**: STOMP over SockJS (`WebSocketConfig`), endpoint from `websocket.url` — `/ws` in all profiles (`/wss` is still permitted in `SecurityConfiguration` but unused). `NotificationService` pushes live updates to clients; the broker prefixes are `/notification` and `/user`, app prefix `/app`.
- **Scheduling** (`@EnableScheduling` in `config/SchedulingConfig`, switched off with `app.scheduling.enabled=false` — only the `test` profile does that): cron expressions come from properties so dev runs them aggressively and prod sparingly.
  - `CartService` recurring-cart job (`task.recurringCart.schedule`) — materializes new `Cart`s from active `CartTemplate`s. Recurrence is modeled as a `CartTemplate` (with `RecurrenceType`, `nextExecutionDate`, `active`); a one-off cart has no template (`RecurrenceType.NONE`). See `../docs/sketches` in the repo root.
  - `AuthenticationService` token cleanup (`task.clearToken.schedule`).
  - `NotificationService` has a `@Scheduled(fixedRate = 10000)` sweep.
- **i18n**: `messages.properties` (German, default), `messages_en.properties`, `messages_es.properties`. Locale is resolved from the `Accept-Language` header (`AppLanguageConfig` → `AcceptHeaderLocaleResolver`, supported locales `de`/`en`/`es`, default `Locale.GERMAN`, no system-locale fallback): regional variants map to their language (`en-US` → `en`), unsupported languages fall back to `de`, so `LocaleContextHolder.getLocale()` only ever returns one of the three. A new language needs an entry there plus a `messages_<lang>.properties`. Use the `Translator` component (`translate(key, args...)`) for any user-facing string — services pass message *keys* (e.g. `"categories.default.settlement"`) rather than literals. Excel export (`ExcelWriter`) is also localized.
- **Email**: Brevo (SendinBlue) via `BrevoMailSenderService` for verification / password reset, configured by `brevo.*` properties.
- **Excel export**: `helper/ExcelWriter` uses Apache POI to stream `.xlsx` spending overviews directly to the `HttpServletResponse`.
- **Dates**: use `helper/DateUtils` (`toLocalDate`/`toDate`, `NO_END_DATE` = 2999-12-31 as the "open-ended" sentinel) instead of ad-hoc `Date`↔`LocalDate` conversions. Time-dependent logic takes "now" from the injected `Clock` bean (`config/ApplicationConfig`, used by all time-dependent services, incl. `JwtService` for token issuing and the parser's expiry check) rather than `LocalDate.now()`/`new Date()`, so tests can control it.
- **Validation**: custom `@ValidEmail` constraint (`validator/`) on auth/user DTOs. Enums such as `RecurrenceType` and `TokenType` live in `constants/`.
- **Health**: Spring Actuator exposes only `/actuator/health`.

### Database / Liquibase

Schema and seed data are managed by Liquibase, **not** JPA. Changelogs live under `src/main/resources/db/changelog/`:
- `db.changelog-master.xml` (dev/test) and `db.changelog-master-prod.xml` (prod) are the two entry points referenced by the respective profiles.
- Each change is a separate file in `release-1.x/` (`release-2.x/` exists but is still empty), included with explicit `context` attributes. In `db.changelog-master.xml`, schema changesets use `context="dev,test,prod"` and seed/demo-data changesets use `context="dev,!test"` so they load in dev but never in tests. `db.changelog-master-prod.xml` includes everything with `context="prod"` (incl. prod-specific data loads like `carts_prod.csv`).
- Seed data is loaded from CSV files in `src/main/resources/db/data/`.
- To change the schema: add a new timestamped changeset file, then `<include>` it in **both** master changelogs with appropriate contexts. Never edit an already-applied changeset.

## Testing

**Spock/Groovy only.** All tests live under `src/test/groovy/com/github/svenfran/budgetapp/budgetappbackend/`; there is no `src/test/java`. JUnit Jupiter and Mockito are still on the classpath (via `spring-boot-starter-test`), so a `*Test`/`*IT` class compiles — but it **silently never runs**. The suffix decides whether and where a spec runs, so check that the test count went up after adding one.

### Test levels

| Suffix | Base | Runs in | Use for |
|---|---|---|---|
| `*Spec` | `spock.lang.Specification` | `./mvnw test` (Surefire), no Docker | rules, calculations, verifications, mappers, recipient selection, message bundles — no Spring, no DB |
| `*IntegrationSpec` | `testsupport/IntegrationSpec` | `./mvnw verify` (Failsafe) | service flows with transactions, permissions via the real `SecurityContext`, own JPQL queries, Liquibase schema, notification interactions |
| `*ApiSpec` | `IntegrationSpec implements ApiRequests` | `./mvnw verify` (Failsafe) | HTTP status, exception mapping, JSON shape of DTOs, security rules, error-message contract |

Test as low as possible: plain logic as `*Spec`; the database only when queries, transactions or several services together are under test; MockMvc only for what is visible at HTTP level.

Layout: the package mirrors `main` (`controller/`, `service/`, `service/mapper/`, `repository/`, `helper/`, `validator/`), the test kind is in the suffix. `testsupport/` is the only test infrastructure. Feature names are German behavior sentences (`def "Nicht-Mitglied erhält 403"()`).

### `IntegrationSpec` (integration and API specs)

- One Postgres container (`PostgresTestContainer`) and **one Spring context** for all specs. It provides `fixtures`, `testClock`, `mockMvc`, `entityManager`, `loginAs(User)` (sets the `SecurityContext`) and `flushAndClear()` (flush SQL and clear the persistence context, so assertions hit the database).
- `NotificationService` and `EmailSenderService` are `@SpringBean` mocks. Assert notifications as interactions, e.g. `1 * notificationService.sendGroupUpdateNotification(groupId, _)`.
- **No context configuration in individual specs** (`@SpringBean`, `@MockBean`, `@TestPropertySource`, `@DirtiesContext`, an own `@SpringBootTest`): each one starts another context (~26 s). If a spec needs a mock, add it to `IntegrationSpec`.
- Full rules: Groovydoc of `testsupport/IntegrationSpec.groovy`.

### API specs

- `class <ControllerClass>ApiSpec extends IntegrationSpec implements ApiRequests` with `@Autowired ObjectMapper objectMapper` — e.g. `CartControllerApiSpec`.
- Send requests through `send(request, user:, body:)`, never `mockMvc.perform` directly. Omitting `user:` means unauthenticated (Spring Security answers **403, not 401**); `user: null` fails on purpose, so a permission test can't turn green by accident. Read JSON with `json(response)` and Groovy comparisons (`body*.title == ['Pizza']`) instead of `jsonPath`.
- URLs are string literals in the test, not constants from production code (they are the contract with the frontend); path variables via MockMvc URI variables (`get('/api/carts/{id}', id)`); an endpoint called several times gets a private helper in the spec.
- Full rules: Groovydoc of `testsupport/ApiRequests.groovy`.

### Test data

- `TestData` — static, **not persisted**: one valid object per call, every field overridable by named argument (`TestData.user(name: 'ben')`). For unit specs and as building blocks.
- `Fixtures` — Spring component, **persisted**, consistent scenarios: `fixtures.group(members: [ben])` returns a `GroupScenario` (`group`, `owner`, `members`, `category`) with membership history; plus `user`, `category`, `cart`, `template`, `token`.
- Defaults are valid, names/e-mails unique, dates fixed (`TestData.REFERENCE_DATE` = 2025-06-15; memberships start one year before, `memberSince:` overrides). Values the test asserts on are set in the test, not hidden in defaults.
- Groovy named args go into the first (`Map`) parameter: `fixtures.cart(wg, title: 'Pizza')`.
- No real tokens or personal data. New `TestData`/`Fixtures` methods only with the first test that needs them; no other helpers or private builders.

### Isolation

- **Default: rollback** via the `@Transactional` of `IntegrationSpec`; service and test share one persistence context. Query only your own data (e.g. by group id), never `findAll()`/`count()` — commit specs may leave rows behind. After `flushAndClear()` compare ids, not entities (no `equals` on entities). No `deleteAll()` setup.
- **Commit-dependent** (scheduler jobs, visibility after commit, lazy loading): `@Transactional(propagation = Propagation.NOT_SUPPORTED)` on the spec and `databaseCleaner.truncateAll()` in `setup()`, i.e. clean **before** each feature.

### Time

- Integration/API specs use `testClock` (replaces the production `Clock`; default `TestData.REFERENCE_DATE`, change it with `testClock.today = LocalDate.of(2025, 1, 31)`, reset after every feature). Unit specs use `Clock.fixed(...)`. Never `LocalDate.now()`/`new Date()` in tests.
- Limit: Hibernate `@CreationTimestamp`/`@UpdateTimestamp` (`dateCreated`, `lastUpdate` on `Cart`, `Group`, `ShoppingList`, `ShoppingItem`) still use the system clock — don't assert on them.

### Mocks

- Unit specs: repositories and `DataLoaderService` as `Mock`/`Stub`; `VerificationService`, mappers and `DateUtils` real. Dependencies are passed via the constructor (`new XyzService(Mock(...), …)`); `src/main` uses constructor injection only (no `@Autowired` fields).
- Integration/API specs: everything real except the two `@SpringBean` mocks above.

### Spock pitfalls

- Conditions inside `if` blocks or closures need `assert` (or `every`), otherwise they check nothing. Every `where:` column must be used.
- **Prefer ids and DTOs in conditions** — when a condition fails, Spock renders every sub-expression, and ids keep the failure output readable. Extract ids first (`def annaId = anna.id`) or assign the value to a variable in `then:` and compare that. Example: `service/SpendingsOverviewServiceIntegrationSpec.groovy`.
- **Entity `toString`** only prints the entity's own scalars and the ids of its `@ManyToOne` references (`entity/EntityToStringSpec`, `EntityToStringIntegrationSpec`). Never print collections, lazy associations or secrets (password, token values) in a new `toString`: a `User` ↔ `Group` cycle throws a `StackOverflowError` in the Surefire/Failsafe listener, which **swallows** the failure (exit 0, one test fewer in the report), and a lazy association triggers queries or a `LazyInitializationException`.
- No reflection on private methods, no empty specs.

### Logging

Every spec logs what it does, so a run can be followed without a debugger. INFO, about 5 lines per iteration; full bodies and large objects only on **DEBUG**. References: `controller/SpendingsOverviewControllerApiSpec.groovy` (API), `service/CartServiceIntegrationSpec.groovy` (integration), `service/RecurringCartsIntegrationSpec.groovy` (commit-dependent, `void` job).

**Central — specs don't repeat it:**
- `testsupport/FeatureLoggingExtension` (global Spock extension, registered in `src/test/resources/META-INF/services/org.spockframework.runtime.extension.IGlobalExtension`) logs `>> <feature/iteration name>` before every iteration of every spec (unit, integration, API), with the spec class as logger. The name is logged unfiltered: **no secret values as `#param` in feature names** — use a property of the case instead (`(gefunden: #gefunden)`, not the token value).
- `ApiRequests.send` logs one INFO line per request: `<METHOD> <URI>[?query] <auth> -> <status>`, e.g. `GET /api/spendings/13/2025 als User 22 -> 200`. Auth is `als User <id>` (`user:`), `mit Bearer-Token` (an `Authorization` header set by the spec; the value is never logged) or `ohne Anmeldung`. On DEBUG it logs `Request-Body:`/`Response-Body:` (UTF-8): every JSON field whose name contains `token` or `password` (any case, nested) becomes `"***"`, unreadable JSON is replaced by a hint, plain-text error messages stay as they are. Headers are not logged.

**Per feature (integration and API specs):**
- SLF4J in the spec: `private static final Logger LOG = LoggerFactory.getLogger(XyzSpec)`.
- End of `given:` (after `flushAndClear()`, ids taken before): `LOG.info('Szenario: …')` with ids and the test data the feature checks. It maps user ids to roles (`Gruppe 13, Owner 22, Mitglied 21`) — the request line only shows the user id — and for Bearer requests says whom the token was issued for. Add `testClock.today` where time matters.
- `when:`, integration: one line per call under test with its result, e.g. `cartService.updateCartAndTemplateIfValid(Cart 13, 2 Mitglieder) -> averagePerMember 30.0`. API: the request logs itself; add `LOG.info('Antwort: …')` with the checked values or the error message (parse the body in `when:`).
- Exception features: the call line goes **before** the call and states the expectation (`…, erwartet UserNotFoundException`); `thrown()` stays as it is.
- `expect:` with a call under test (integration specs): use `when:` (call once, keep the result in a variable, log it) + `then:` (same condition on the variable). API specs may keep `send(...).status == …` in `expect:` — the request line is logged centrally.
- `void` calls whose result is read in `then:`: move the existing leading result queries (assignments such as `def saved = cartRepository.findById(...)`) to the end of `when:` and log them there. Only assignments, conditions stay in `then:`; keep the order of queries and `flushAndClear()`; no extra queries just for logging (then log `… aufgerufen` and the start state in the scenario); lazy associations stay in `then:` (matters in commit-dependent specs).
- Never log in `then:`/`expect:` — statements there are conditions.
- Log ids, numbers, enum values and DTO fields — never secrets (token, password values) or entities with collections or lazy associations (see Spock pitfalls above). Entity `toString` texts only on DEBUG.

**Unit specs** log nothing themselves; the central `>>` line is enough.
- Where to read it: the Maven/CI console, and per test case in `<system-out>` of the XML report `TEST-<fully.qualified.Spec>.xml` (`target/surefire-reports/` for unit specs, `target/failsafe-reports/` for integration and API specs). As a plain file per spec: add `-Dmaven.test.redirectTestOutputToFile=true` → `<fully.qualified.Spec>-output.txt` in the same directory (console stays empty). In IntelliJ, select the feature in the test tree. Full bodies: add `-Dlogging.level.com.github.svenfran.budgetapp.budgetappbackend=DEBUG` (or `….controller=DEBUG` for the API specs only).
- Configuration:
  - `src/test/resources/logback-test.xml` holds the line format and the library levels (root INFO; Spring test bootstrap/`TransactionContext`, Liquibase, Testcontainers on WARN). Logback loads it on its first initialization, so it covers unit specs and the Spring test bootstrap too; Spring Boot reloads it at context start and then applies `logging.level.*` from the properties on top. New library levels go into the XML.
  - `application-test.properties` only holds what must override `application.properties` (app package INFO instead of TRACE, Spring Security WARN instead of DEBUG) plus `spring.main.banner-mode=off`. A logger set there wins over the XML in Spring specs — don't set the same logger in both places. `logging.pattern.console` has no effect (own appender).
- Line format without colors, date, PID or thread: `11:56:17.976 INFO  SpendingsOverviewControllerApiSpec : …`; stack traces in full.
- DEBUG: `-Dlogging.level.<package>=DEBUG` works in integration and API specs. Unit specs (no Spring) only read the XML — change the level there locally.
- Not suppressible by logging: Liquibase 4.9 prints `Running Changeset: …` (one line per changeset, once per run) straight to stdout.

### Error-message contract

The frontend recognizes some error cases by the text of the backend message. The list of these messages (M1–M10, X1–X2: frontend location, expected status and text, covering spec) is in the Groovydoc of `controller/CartControllerApiSpec.groovy`; each one has an API test. When you change such a message or add a frontend check on `err.error`, update the test and the list. **Stopgap until error codes are decided** — not a permanent contract.

### Reference specs

| Kind | Spec |
|---|---|
| Unit | [`service/VerificationServiceSpec.groovy`](src/test/groovy/com/github/svenfran/budgetapp/budgetappbackend/service/VerificationServiceSpec.groovy) |
| Integration with fixtures (rollback) | [`service/CartServiceIntegrationSpec.groovy`](src/test/groovy/com/github/svenfran/budgetapp/budgetappbackend/service/CartServiceIntegrationSpec.groovy) |
| Commit-dependent integration + `testClock` | [`service/RecurringCartsIntegrationSpec.groovy`](src/test/groovy/com/github/svenfran/budgetapp/budgetappbackend/service/RecurringCartsIntegrationSpec.groovy) |
| API (MockMvc) + error-message contract | [`controller/CartControllerApiSpec.groovy`](src/test/groovy/com/github/svenfran/budgetapp/budgetappbackend/controller/CartControllerApiSpec.groovy) |

### Test counts

PRs state the test count before/after. The Surefire/Failsafe reports count a data-driven Spock feature as its iterations **plus one** — compare like with like (or convert to iterations). Reports of earlier runs stay in `target/` until `clean`. If IntelliJ is open on the project, it can wipe `target/` during `verify` (`ClassNotFoundException` for an unchanged spec) — rerun once before debugging.
