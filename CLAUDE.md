# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working in the `budget-app` monorepo ("Divvy").

Project-specific details live in the sub-project files — read the relevant one before working in that directory; this file only covers what spans both:

- [`ionic-frontend/CLAUDE.md`](ionic-frontend/CLAUDE.md) — Ionic 8 / Angular 19 / Capacitor app (commands, signals-based state, WebSocket client, Android flavors).
- [`spring-backend/CLAUDE.md`](spring-backend/CLAUDE.md) — Spring Boot 2.7 / Java 17 API (commands, profiles, layered architecture, Liquibase, tests).

## Repository layout

| Path | Content |
|---|---|
| `ionic-frontend/` | Mobile client (Android via Capacitor). Talks to the backend over REST + STOMP/SockJS. |
| `spring-backend/` | REST API, WebSocket broker, scheduled jobs, Postgres via Liquibase. |
| `railway.json` | Railway deployment of the backend (builds `spring-backend/Dockerfile`, `prod` profile). |
| `docs/sketches`, `docs/sketches`, `docs/sketches` | Design notes for recurring carts (German, partly untracked). |

## How frontend and backend fit together

- **REST**: all endpoints under `/api` (`/api/auth` public). The frontend's `AuthHttpInterceptorService` sends `Authorization` (JWT), `DeviceId` and `Accept-Language` on every request; the backend uses `Accept-Language` for i18n and `DeviceId` for token handling.
- **Backend URL**: frontend `src/config/config.ts` (dev, LAN IP on port 9090 = backend `dev` profile) / `config.prod.ts` (Railway). New client origins must be added to the backend's `app.cors.allowed-origins`, not in code.
- **DTO contract**: backend `dto/` classes ↔ frontend `model/*Dto` interfaces. There is no shared schema or code generation — a field change must be made on both sides by hand.
- **WebSocket** (`/ws`, STOMP over SockJS): backend `NotificationService` pushes to `/user/{userId}/notification/<action>` for every group member **except the user who triggered the change** (that client relies on its own optimistic update). Plus a global `/notification/health` heartbeat every 10 s.
  - Currently synced live: groups, members, owner change, shopping lists and items (where each is subscribed: see `ionic-frontend/CLAUDE.md`).
  - **Not** synced live: carts, categories, settlements — other members only see them on refetch.
  - Notifications are sent from inside the `@Transactional` service method, i.e. **before commit**. Entities have no `@Version` / optimistic locking, so concurrent edits are last-write-wins.
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
- **Frontend state** — signal services with optimistic update + rollback, `groupId` filter against the active group, refetch triggers (`triggerUpdate()`).

Also check explicitly:
- **Permissions** — who may perform the change (member, owner, removed member); no endpoint without a `VerificationService` check.
- **Transactions** — the change and its dependent writes (e.g. `Group.lastUpdate*`, templates, history) belong in one `@Transactional` method.
- **Concurrent changes** — several members may edit the same data at once (last-write-wins today); consider what happens to a stale client state and to the optimistic update/rollback.
- **Notification timing** — notifications currently go out before commit, so a failing commit or a fast refetch can let clients see state that isn't persisted (yet). Keep this in mind for new notifications; changing the mechanism (e.g. after-commit events) is a separate proposal.

If a data type is currently not synced live (e.g. carts), don't add sync as a side effect — mention it as a proposal.

### Future: offline shopping lists
Shopping lists are meant to become offline-capable at some point. **This is a future requirement — do not implement it (local persistence, sync queues, conflict handling, etc.) without an explicit request.** Avoid choices in shopping-list code that would make it harder later (e.g. more coupling to the online-only `server-unavailable` flow), and point it out when a story touches it.

### Tests
- Respect existing tests and conventions; keep them green and adjust them when behavior changes.
  - Backend: Spock/Groovy preferred for new tests (unit in `service/unit/`, integration via Testcontainers in `service/integration/`). Needs Docker; with Docker 29+ set `DOCKER_API_VERSION=1.44`.
  - Frontend: Karma + Jasmine `*.spec.ts` next to the code; currently mostly generated "should create" stubs.
- The overall test concept will be reworked separately — don't build out new test infrastructure or restructure tests on your own initiative.

### Other
- Don't modify these CLAUDE.md files as a side effect of a story; propose updates instead if they're outdated.
- Never commit or print secrets (`spring-backend/.env`, `ionic-frontend/android/keystore.properties`, keystores).

## Feature workflow

- Jira project see: `.ai/jira-integration.md`
- Implementation plans: docs/plans/<identifier>/implementierungs-plan.md
