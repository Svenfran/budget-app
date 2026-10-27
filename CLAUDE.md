# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working in the `budget-app` monorepo ("Divvy").

Project-specific details live in the sub-project files — read the relevant one before working in that directory; this file only covers what spans both:

- [`ionic-frontend/CLAUDE.md`](ionic-frontend/CLAUDE.md) — Ionic 8 / Angular 19 / Capacitor app (commands, signals-based state, WebSocket client, Android flavors, tests).
- [`spring-backend/CLAUDE.md`](spring-backend/CLAUDE.md) — Spring Boot 2.7 / Java 17 API (commands, profiles, layered architecture, Liquibase, tests).

## Repository layout

| Path | Content |
|---|---|
| `ionic-frontend/` | Mobile client (Android via Capacitor). Talks to the backend over REST + STOMP/SockJS. |
| `spring-backend/` | REST API, WebSocket broker, scheduled jobs, Postgres via Liquibase. |
| `railway.json` | Railway deployment of the backend (builds `spring-backend/Dockerfile`, `prod` profile). |
| `docs/` | **Local only** (`/docs/*` is gitignored), German: implementation plans (`plans/`), reviews (`reviews/`), test architecture concept (`architecture/`), design notes for recurring carts (`sketches/`). Not available on a fresh checkout — don't make versioned files depend on it. |

## How frontend and backend fit together

- **REST**: all endpoints under `/api` (`/api/auth` public). The frontend's `AuthHttpInterceptorService` sends `Authorization` (JWT), `DeviceId` and `Accept-Language` on every request; the backend uses `Accept-Language` for i18n and `DeviceId` for token handling.
- **Backend URL**: frontend `src/config/config.ts` (dev, LAN IP on port 9090 = backend `dev` profile) / `config.prod.ts` (Railway) — both git-ignored, created from `src/config/config.example.ts`. New client origins must be added to the backend's `app.cors.allowed-origins`, not in code.
- **DTO contract**: backend `dto/` classes ↔ frontend `model/*Dto` interfaces. There is no shared schema or code generation — a field change must be made on both sides by hand.
- **WebSocket** (`/ws`, STOMP over SockJS): CONNECT needs the same credentials as REST (`Authorization: Bearer <jwt>` + `DeviceId`), the session principal is the userId. Backend `NotificationService` sends via `convertAndSendToUser(userId, "/notification/<action>")` to every group member **except the user who triggered the change** (that client updates its own state from the REST response); clients subscribe to `/user/notification/<action>` (no userId in the topic). Plus a global `/notification/health` broadcast (`{status}`) every 10 s. Clients may only subscribe to these destinations and never SEND.
  - Currently synced live: groups, members, owner change, shopping lists and items including their order (`reorder-lists`/`reorder-items`; where each is subscribed: see `ionic-frontend/CLAUDE.md`).
  - **Not** synced live: carts, categories, settlements — other members only see them on refetch.
  - Notifications are sent **after commit** (`TransactionSynchronization.afterCommit` in `NotificationService`; immediately if no transaction is active). Recipients and payload are determined inside the transaction; a rollback sends nothing. Entities have no `@Version` / optimistic locking, so concurrent edits are last-write-wins.
  - After a reconnect or a resume from background (> 30 s), the app silently re-fetches the live-synced data (groups, members, shopping lists), throttled to once per 60 s; carts, categories and statistics are not re-fetched.
- **i18n**: both sides support `de` (default) / `en` / `es`. User-facing strings need keys in all three files on the respective side (frontend `src/assets/i18n/*.json`, backend `messages*.properties`).
- **Language**: comments, notes and many identifiers are German on both sides; documentation files are English.

## Working rules

### Approach
- **Larger features: analyze first and present a plan for approval** before implementing (affected modules, endpoints/DTOs, WebSocket topics, schema changes, open questions).
- **Name unclear requirements and architecture decisions explicitly** and ask, instead of silently making assumptions. If a minor assumption is unavoidable, state it.
- **After implementing, report what was actually verified**: which builds, tests, lint or manual checks ran (and their result), what was *not* run, and remaining risks. Don't claim checks that weren't executed.

### Scope
1. **Check both sub-projects for impact** on every feature or change (DTOs, endpoints, WebSocket topics, i18n keys, validation, error handling). Only change the side(s) where a change is actually needed — and say briefly when the other side was checked and is unaffected.
2. **Reuse existing architecture, patterns and conventions** (see sub-project files) instead of introducing new libraries, state mechanisms or structures.
3. **Keep changes small, traceable and limited to the story.** No large refactorings, architecture changes or unrequested changes.
4. Small, low-risk improvements to readability, maintainability, consistency or efficiency are fine **in the directly affected code**, as long as they don't noticeably widen the scope.
5. **Larger optimizations or refactoring opportunities: identify and propose them separately** (at the end of the response), don't implement them unasked.

### Shared group data
Group data (groups, members, carts, categories, shopping lists/items, settlements) is seen by several users at once. When changing it, consider all three layers together:
- **REST API** — endpoint, DTO, `VerificationService` check, typed exception + mapping in `RestResponseEntityExceptionHandler`.
- **WebSocket sync** — does the change need a (new or changed) notification in `NotificationService`, and a matching subscription in the frontend? Keep topic names and payload DTOs identical on both sides; remember the triggering user receives no notification.
- **Frontend state** — signal services that update state after a successful response and leave it unchanged on error (no snapshot restore; the shopping-list order is the one optimistic exception, see `ionic-frontend/CLAUDE.md`), `groupId` filter against the active group, refetch triggers (`triggerUpdate()`).

Also check explicitly:
- **Permissions** — who may perform the change (member, owner, removed member); no endpoint without a `VerificationService` check.
- **Transactions** — the change and its dependent writes (e.g. `Group.lastUpdate*`, templates, history) belong in one `@Transactional` method.
- **Concurrent changes** — several members may edit the same data at once (last-write-wins today); consider what happens to a stale client state and to the state update/rollback.
- **Notification timing** — notifications go out after commit (see above), so call the `NotificationService.send*` methods inside the `@Transactional` service method as before; they are deferred automatically. A send failure after commit is only logged and does not fail the request.

If a data type is currently not synced live (e.g. carts), don't add sync as a side effect — mention it as a proposal.

### Future: offline shopping lists
Shopping lists are meant to become offline-capable at some point. **This is a future requirement — do not implement it (local persistence, sync queues, conflict handling, etc.) without an explicit request.** Avoid choices in shopping-list code that would make it harder later (e.g. more coupling to the online-only `server-unavailable` flow), and point it out when a story touches it.

### Tests
The test conventions live in the sub-project files: [`spring-backend/CLAUDE.md` → Testing](spring-backend/CLAUDE.md#testing) (Spock only, test kind by class suffix, `IntegrationSpec`, `TestData`/`Fixtures`) and [`ionic-frontend/CLAUDE.md` → Testing](ionic-frontend/CLAUDE.md#testing) (Karma + Jasmine, helpers in `src/testing/`). Follow them for every new or changed test.

- **Before every PR**, for each affected side (CI runs them too, see below):
  - backend: `./mvnw verify` in `spring-backend/` (all specs; Docker must be running),
  - frontend: `npm run test:ci`, `npm run lint` and `npm run build` in `ionic-frontend/`.
- **CI**: GitHub Actions (`.github/workflows/ci.yml`) runs the affected side(s) on every PR and push to `main` (path filter; a change to the workflow runs both). The check `CI result` must be green to merge. Test reports are attached to the run as artifacts `backend-test-reports` / `frontend-test-report`.
- **New or changed behavior gets tests** on the lowest level that can show it (unit before integration before API). No generated "should create" stubs, no empty tests.
- **Bugfixes need a regression test** that fails without the fix and passes with it. If that's not reasonably possible, say why in the PR.
- **Every PR states the commands that were run and the test count before/after** (backend unit and integration separately, frontend). A spec with the wrong suffix or file name silently doesn't run — a test count that didn't go up is the only signal.
- **Error-message contract:** the frontend recognizes some error cases by the text of the backend message (`includes(...)` or `===` on `err.error`). Each such message is pinned by an API test; the list lives in the Groovydoc of `CartControllerApiSpec` (see backend Testing). Changing a message or a frontend check means updating both sides and that list. This is a stopgap until error codes are decided.
- Keep existing tests green and adjust them when behavior changes. The test infrastructure is in place — don't add test frameworks, base classes or helpers on your own initiative; extend `TestData`/`Fixtures`/`src/testing/` when a story needs it.

### Pull requests
- **Keep PR descriptions short** (roughly 15–25 lines, readable in under a minute): what and why in 1–3 sentences, the changed areas as a few bullets, the test commands with counts before/after (required, see Tests), and only noteworthy risks, limits or follow-up tickets.
- Leave out what the diff, the commits, the plan or a review already show: no per-commit or per-phase lists, no review summaries, no restated acceptance criteria, no explanations of unchanged areas beyond one line.
- Write the description in German, like the existing PRs.

### Other
- Don't modify these CLAUDE.md files as a side effect of a story; propose updates instead if they're outdated.
- Never commit or print secrets (`spring-backend/.env`, `ionic-frontend/android/keystore.properties`, keystores).

## Feature workflow

- Jira project see: `.ai/jira-integration.md`
- Implementation plans: docs/plans/<identifier>/implementierungs-plan.md
