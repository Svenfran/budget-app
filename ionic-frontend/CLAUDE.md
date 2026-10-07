# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

"Divvy" is an Ionic 8 + Angular 19 mobile app (Capacitor, Android target) for shared-expense / budget management across groups. Users belong to groups, record purchases ("carts") against categories, manage shared shopping lists, and settle balances between members. The backend is a separate Spring Boot service (REST + STOMP/SockJS WebSocket); this repo is the frontend only.

Note: the codebase is written largely in **German** — comments, alert text source keys, and many identifiers. UI strings are translated via i18n (de/en/es).

## Commands

```bash
npm start                  # ng serve, dev server in browser
npm run build              # ng build (production by default)
npm run build:dev          # ionic build, development config (APP_ENV=dev)
npm run build:prod         # ionic build, production config (APP_ENV=prod)
npm test                   # ng test — Karma + Jasmine, all *.spec.ts
npm run lint               # ng lint — ESLint (angular-eslint)

# Run a single test: use fdescribe/fit in the spec, or:
ng test --include='**/group.service.spec.ts'

# Android (Windows, requires a connected device — check-device runs adb first)
npm run android:dev        # sync + build/install Dev flavor debug APK (de.svenfran.divvyapp.dev)
npm run android:prod       # sync + build/install Prod flavor *release* APK (de.svenfran.divvyapp)
npm run android:prod:apk   # sync + build Prod release APK only (no device needed, no install)
```

## Environment & build configuration

The backend URL is **not** taken straight from `environment.ts`. The chain is:

- `src/config/config.ts` (dev: `http://192.168.178.23:9090`) and `src/config/config.prod.ts` (prod: Railway URL) hold `appConfig.baseUrl`.
- `angular.json` `fileReplacements` swaps `config.ts` → `config.prod.ts` for the production build.
- `environment.ts` (the only environment file — there is no `environment.prod.ts`) derives `apiBaseUrl` and `websocketUrl` (`${baseUrl}/ws`) from `appConfig`. Its `production` flag is always `false`; don't rely on it to detect prod.
- `capacitor.config.ts` switches `appId`/`appName`/`cleartext`/`androidScheme` (`http` for dev, `https` otherwise) based on the `APP_ENV` env var (`dev` vs anything else), set via `cross-env` in the npm scripts.

When pointing the app at a different backend, edit `src/config/config.ts` (dev) — not `environment.ts`.

### Android flavors & signing

- `android/app/build.gradle` defines an `environment` flavor dimension with `dev` and `prod` flavors (separate application IDs, so both can be installed side by side).
- Cleartext HTTP (needed for the local dev backend) is enabled **only for the dev flavor** via `android/app/src/dev/AndroidManifest.xml` + `src/dev/res/xml/network_security_config.xml`. The main manifest does not allow cleartext, so the prod build can only reach HTTPS backends.
- Release builds are signed using `android/keystore.properties` (git-ignored, as are `*.jks`/`*.keystore`). Copy `android/keystore.properties.example` and fill it in. If the file is missing, Gradle logs a warning and the release APK stays **unsigned**, so `android:prod` can't install it.

## Architecture

**Module structure.** Classic Angular NgModule app (not standalone components — `AppComponent` and most pages set `standalone: false`). Every page is its own lazy-loaded feature module wired in `app-routing.module.ts`, guarded by `AuthGuard` (`canLoad`). The main shell is `domains/tabs` (`domains-routing.module.ts`) with child tabs `overview`, `cartlist`, `shoppinglist`. The `cartlist/new-edit/:id` route is the add/edit-purchase form.

**State management = Angular signals in singleton services.** There is no NgRx. Root-provided services own `WritableSignal` state that components read directly:
- `GroupService` — `activeGroup`, `groupsSideNav`, `groupOverviewList`, `groupMembers`, `hasNoGroups`. The **active group** is the central pivot of the whole app; most data is fetched per `activeGroup().id` and persisted to device storage under key `ACTIVE_GROUP`.
- `CartService` (`domains/cartlist/service`) — `cartList`, plus `computed` `sum`/`count`.
- `CategoryService`, `HealthCheckService` (`backendStatus`), etc.

Cross-component "something changed, refetch" signaling uses an incrementing counter signal (e.g. `cartUpdated`, `memberUpdated`) bumped via `triggerUpdate()` and watched with `effect()`.

**Mutations are optimistic with rollback.** Service write methods (e.g. `GroupService.addGroup/updateGroup/deleteGroup`) snapshot current signal state, update the signal immediately, fire the HTTP call, and on error reset the signal back to the snapshot.

**Auth.** `AuthService` holds the user in a `BehaviorSubject`, with a JWT-style token. Token + auth data persist via `StorageService` (`authData`) and `localStorage` (`token`). `autoLogin()` restores on launch and `autoLogout()` schedules a `setTimeout` to log out at `expirationDate`. `AuthHttpInterceptorService` (registered as a multi `HTTP_INTERCEPTORS` in `app.module.ts`) attaches `Authorization`, `DeviceId`, and `Accept-Language` headers to every request.

**Real-time updates.** `WebSocketService` connects via STOMP over SockJS to `${baseUrl}/ws`. `AppComponent.subscribeToTopics()` subscribes to per-user topics (`/user/{userId}/notification/...`) for group add/update/delete, member changes, owner changes, and a global `/notification/health` topic. Incoming messages mutate the same service signals, keeping all open views in sync. `subscribe()` filters out messages whose `groupId` ≠ the active group for list/item topics.

**Health / offline handling.** `HealthCheckService` polls `/actuator/health`; the `/notification/health` WebSocket topic pushes `UP`/`DOWN`. On `DOWN` the app routes to `/server-unavailable`; on recovery it returns to the overview. When a user has no groups, the app routes to `/no-group`.

**Persistence.** `StorageService` wraps Capacitor `Preferences`. Two API styles coexist: `setData/getData` (wraps value in `{data}`, used for auth) and `setItem/getItem<T>` (used for `ACTIVE_GROUP`, `darkMode`). Some tokens also live in `localStorage`.

**i18n.** `@ngx-translate/core` with a `CustomTranslateLoader`. Languages `de`/`en`/`es` in `src/assets/i18n/*.json`; default `de`. `LanguageService` persists choice in `localStorage` (`appLang`) and is mirrored to the `Accept-Language` header by the interceptor. Use `translate.instant('key.path')` for alert/toast text rather than hardcoded strings.

**Theming.** Dark/light theme toggled via the `color-theme` body attribute (set with `Renderer2`), persisted as `darkMode` in Preferences, with native status/navigation bar colors set through Capacitor plugins on mobile.

## Conventions

- Constants and default/sentinel objects live in `src/app/constants/` (`default-values.ts` `INIT_VALUES`/`Init`, `recurrence-type.ts`). The "DEFAULT" group flag (`INIT_VALUES.DEFAULT`) marks the empty/placeholder active group and is checked before fetching data.
- Per-feature `model/` folders hold DTO interfaces; suffix `Dto` denotes a backend payload shape.
- New pages: generate as a lazy-loaded NgModule, add to `app-routing.module.ts` with `canLoad: [AuthGuard]`, and keep page-specific state in a root-provided signal service rather than the component.
