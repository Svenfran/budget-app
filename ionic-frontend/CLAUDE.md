# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

"Divvy" is an Ionic 8 + Angular 19 mobile app (Capacitor, Android target) for shared-expense / budget management across groups. Users belong to groups, record purchases ("carts") against categories, manage shared shopping lists, and settle balances between members. This directory (`ionic-frontend`) is the client module of the `budget-app` monorepo; the sibling `spring-backend` is the Spring Boot API (REST + STOMP/SockJS WebSocket). Cross-module architecture and working rules are in the root `CLAUDE.md`; backend details in `spring-backend/CLAUDE.md`.

Note: the codebase is written largely in **German** — comments, alert text source keys, and many identifiers. UI strings are translated via i18n (de/en/es).

## Commands

```bash
npm start                  # ng serve, dev server in browser
npm run build              # ng build (production by default)
npm run build:dev          # ionic build, development config (APP_ENV=dev)
npm run build:prod         # ionic build, production config (APP_ENV=prod)
npm run test:ci            # Karma + Jasmine, single headless run (ChromeHeadless) — run before every PR
npm test                   # Karma in a Chrome window, watch mode (local debugging)
npm run lint               # ng lint — ESLint (angular-eslint)

# Run a single spec (a pattern that matches nothing runs 0 tests and still reports SUCCESS):
npm run test:ci -- --include='**/cart.service.spec.ts'

# Android (Windows, requires a connected device — check-device runs adb first)
npm run android:dev        # sync + build/install Dev flavor debug APK (de.svenfran.divvyapp.dev)
npm run android:prod       # sync + build/install Prod flavor *release* APK (de.svenfran.divvyapp)
npm run android:prod:apk   # sync + build Prod release APK only (no device needed, no install)
```

## Environment & build configuration

The backend URL is **not** taken straight from `environment.ts`. The chain is:

- `src/config/config.ts` (dev: LAN IP of the backend machine, port 9090) and `src/config/config.prod.ts` (prod: Railway URL) hold `appConfig.baseUrl`. Both are **git-ignored** and missing on a fresh checkout: copy the versioned template `src/config/config.example.ts` to `config.ts` (needed for `npm start`, `npm run test:ci` and dev builds) and to `config.prod.ts` (needed for the production build, i.e. `npm run build`, `android:prod`), then set the URLs. Without them the build fails with `Can't resolve 'src/config/config'`.
- `angular.json` `fileReplacements` swaps `config.ts` → `config.prod.ts` for the production build.
- `environment.ts` (the only environment file — there is no `environment.prod.ts`) derives `apiBaseUrl` and `websocketUrl` (`${baseUrl}/ws`) from `appConfig`. Its `production` flag is always `false`; don't rely on it to detect prod.
- `capacitor.config.ts` switches `appId`/`appName`/`cleartext`/`androidScheme` (`http` for dev, `https` otherwise) based on the `APP_ENV` env var (`dev` vs anything else), set via `cross-env` in the npm scripts.

When pointing the app at a different backend, edit `src/config/config.ts` (dev) — not `environment.ts`.

### Android flavors & signing

- `android/app/build.gradle` defines an `environment` flavor dimension with `dev` and `prod` flavors (separate application IDs, so both can be installed side by side).
- Cleartext HTTP (needed for the local dev backend) is enabled **only for the dev flavor** via `android/app/src/dev/AndroidManifest.xml` + `src/dev/res/xml/network_security_config.xml`. The main manifest does not allow cleartext, so the prod build can only reach HTTPS backends.
- Release builds are signed using `android/keystore.properties` (git-ignored, as are `*.jks`/`*.keystore`). Copy `android/keystore.properties.example` and fill it in. If the file is missing, Gradle logs a warning and the release APK stays **unsigned**, so `android:prod` can't install it.

## Architecture

**Module structure.** Classic Angular NgModule app (not standalone components — `AppComponent` and most pages set `standalone: false`). Every page is its own lazy-loaded feature module wired in `app-routing.module.ts`, guarded by `AuthGuard` (`canLoad` + `canActivate`: `canLoad` only runs when a module is first loaded, `canActivate` also covers returning to an already loaded page, e.g. browser back after logout). The main shell is `domains/tabs` (`domains-routing.module.ts`) with child tabs `overview`, `cartlist`, `shoppinglist`. The `cartlist/new-edit/:id` route is the add/edit-purchase form.

**State management = Angular signals in singleton services.** There is no NgRx. Root-provided services own `WritableSignal` state that components read directly:
- `GroupService` — `activeGroup`, `groupsSideNav`, `groupOverviewList`, `groupMembers`, `hasNoGroups`. The **active group** is the central pivot of the whole app; most data is fetched per `activeGroup().id` and persisted to device storage under key `ACTIVE_GROUP`.
- `CartService` (`domains/cartlist/service`) — `cartList`, plus `computed` `sum`/`count`.
- `CategoryService`, `HealthCheckService` (`backendStatus`), etc.

Cross-component "something changed, refetch" signaling uses an incrementing counter signal (e.g. `cartUpdated`, `memberUpdated`) bumped via `triggerUpdate()` and watched with `effect()`.

**Mutations update state after success.** Service write methods (e.g. `GroupService.addGroup/updateGroup/deleteGroup`, `CartService.addCart`, `ShoppinglistService.addShoppingList`) fire the HTTP call and update the signals from the response in `next`; on error they leave the state as it is and only report it (`CartService` shows an error alert, the shopping-list service a toast — except for checking off an item, which stays silent with the future offline story in mind). There is deliberately no snapshot restore: the state hasn't changed before the response, so a restore would undo nothing but could discard changes that arrived meanwhile (live events, a concurrent successful request). The one optimistic update is the shopping-list order (see Real-time updates).

**Auth.** `AuthService` holds the user in a `BehaviorSubject`, with a JWT-style token. Token + auth data persist via `StorageService` (`authData`) and `localStorage` (`token`). `autoLogin()` restores on launch and `autoLogout()` schedules a `setTimeout` to log out at `expirationDate` and then navigates to `/auth`. `logout()` itself does not navigate — manual logout paths (side menu, profile page, `UserprofileService`) navigate to `/auth` themselves. `AuthHttpInterceptorService` (registered as a multi `HTTP_INTERCEPTORS` in `app.module.ts`) attaches `Authorization`, `DeviceId`, and `Accept-Language` headers to every request.

**Real-time updates.** `WebSocketService` connects via STOMP over SockJS to `${baseUrl}/ws` (one client per logged-in user; `Authorization` and `DeviceId` are set before every (re)connect, `AppComponent.initUser()` disconnects on logout/user change). Topics are `/user/notification/<action>` (no userId) and are registered once in the service's registry via `subscribe(topic, handler, { owner, filterByActiveGroup })`; the service re-subscribes them after every reconnect and keeps them across `disconnect()`, `unsubscribeOwner(owner)` removes them. Registrations are split by feature:
- `AppComponent.subscribeToTopics()` — group update/delete, member add/remove, owner change, and the global `/notification/health` topic.
- `domains/shoppinglist/shoppinglist.page.ts` — shopping list add/update/delete, shopping item add/update/delete/delete-all and the order topics `reorder-lists`/`reorder-items`. While a drag is active, `ShoppinglistService` holds back every asynchronous change of the lists (these WebSocket updates as well as GET, REST and reorder responses; `holdUpdates`/`releaseUpdates`/`applyOrDefer`) and applies them after the drop; held-back changes are dropped after a group/session change.

Incoming messages are parsed centrally (handler errors are logged, never thrown) and mutate the same service signals via pure functions (`service/group-events.ts`, `domains/shoppinglist/shoppinglist-events.ts`), keeping all open views in sync. With `filterByActiveGroup`, messages whose `groupId` ≠ the active group are dropped (list/item topics).

**Shopping-list order (DIV-1).** Lists and items are reordered by drag & drop (Angular CDK, start after touch-and-hold via `cdkDragStartDelay`). `ShoppinglistService` keeps a confirmed order per scope (list order of the group, item order per list: server revision + full id order) in pure functions in `domains/shoppinglist/shoppinglist-order.ts`: a move is shown immediately, only a newer revision (PUT response, WebSocket event, GET with ≥) replaces the confirmed order, and once no own request is pending the visible order falls back to the confirmed one — on error with a toast and a silent refetch. A context generation (active group + `session()`) discards responses that arrive after a switch, including logout without reload. Page specifics: the page scrolls in its own `div.ion-content-scroll-host` with `cdkScrollable` (`ion-content [scrollY]="false"`, also used by `ion-refresher`) so the CDK can auto-scroll; list headers override the CDK's `touch-action: none` with `pan-y` so they still scroll; lists use their own `*cdkDragPreview`/`*cdkDragPlaceholder`, because the CDK's clone of an `ion-input` breaks the drag start.

**Resync.** After a reconnect (`WebSocketService.reconnected$`) or a resume after > 30 s in the background, `AppComponent.resyncLiveData()` silently re-fetches groups, overview, members and the shopping lists of the active group (`LoadOptions { silent, isCurrent }`: no clearing, errors keep the old state, stale answers from an ended login session or another group are dropped), throttled by `service/resync-throttle.ts` (max. once per 60 s, only while connected). The tab load effects track `GroupService.activeGroupId()` and `session()` (not `activeGroup()`), so a rename doesn't reload a tab, but a group change or a new login session does.

**Health / offline handling.** `HealthCheckService` queries `/actuator/health` on demand (login, resume, WebSocket close, reload button; 10 s timeout); the `/notification/health` WebSocket topic pushes `UP`/`DOWN`. On `DOWN` the app routes to `/server-unavailable`; on recovery it returns to the overview. When a user has no groups, the app routes to `/no-group`.

**Persistence.** `StorageService` wraps Capacitor `Preferences`. Two API styles coexist: `setData/getData` (wraps value in `{data}`, used for auth) and `setItem/getItem<T>` (used for `ACTIVE_GROUP`, `darkMode`). Some tokens also live in `localStorage`.

**i18n.** `@ngx-translate/core` with a `CustomTranslateLoader`. Languages `de`/`en`/`es` in `src/assets/i18n/*.json`; default `de`. `LanguageService` persists choice in `localStorage` (`appLang`) and is mirrored to the `Accept-Language` header by the interceptor. Use `translate.instant('key.path')` for alert/toast text rather than hardcoded strings.

**Theming.** Dark/light theme toggled via the `color-theme` body attribute (set with `Renderer2`), persisted as `darkMode` in Preferences, with native status/navigation bar colors set through Capacitor plugins on mobile.

## Testing

Karma + Jasmine. `npm run test:ci` (needs a local Chrome/Chromium and `src/config/config.ts`, see Environment — tests mock all HTTP, so the template's URL is fine) is the run that counts; `npm run lint` and `npm run build` belong to the verification as well. The test target loads `src/window-global-fix.ts` (needed by `sockjs-client`), configured in `angular.json` and `tsconfig.spec.json`.

**Test kinds**
- **Service tests** (the focus): `*.service.spec.ts` next to the service, `TestBed` + `provideServiceTesting()` + `HttpTestingController`, `afterEach(() => http.verify())`.
- **Plain unit tests** for pure functions (filters, sorting, date/sum logic): no `TestBed`. Move logic out of a service into a function when that makes it testable.
- **Component tests** only when the component has its own logic: `IonicModule.forRoot()` + `provideServiceTesting()` + service fakes.

**No generated stubs.** There are no "should create" specs, and `angular.json` `schematics` keep `ng generate` from creating any (`skipTests: true` for the Angular schematics, `spec: false` for the Ionic `page`/`component`). Every spec is written on purpose.

**Helpers live only in `src/testing/`**
- `test-providers.ts`: `provideServiceTesting()` (mocked HTTP, empty router, `TranslateService` without loader — `instant()` returns the key, so alerts are asserted by key), `alertServiceSpy()` (`showErrorAlert`, `showToast`), `storageServiceFake()` (in-memory `StorageService`, stores JSON like the original — never let tests touch Capacitor Preferences) and `authServiceStub(user)` (`user`/`token`/`userIsAuthenticated` observables plus `logout`/`setUserData`/`login`/`register`/`getDeviceId`/`autoLogin` spies; `autoLogin` returns `false` by default).
- `test-data.ts`: factories `aUser`, `aGroup`, `aCategory`, `aCart`, `aShoppingList`, `aShoppingItem` with `Partial<T>` overrides, unique ids and the fixed `REFERENCE_DATE` (2025-06-15); `aLoggedInUser()` returns a `User` whose token expires one hour after `REFERENCE_DATE` — combine it with `jasmine.clock().mockDate(REFERENCE_DATE)` (and `uninstall()` in `afterEach`), since `User.token` checks the current time. Never use the current date in tests.
- Because the loader-less `TranslateService` doesn't interpolate, alert parameters (e.g. `{userName}`) are asserted on the call: `spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough()`, then `toHaveBeenCalledWith(key, params)`.
- Add a factory or helper only when a story needs it.

**What to assert**
- The request (URL, method, body), the signal state after success **and** after an error, the `triggerUpdate()` counters, and alert keys.
- Pin the actual behavior: services update state only after success, so after an error the state is unchanged — including changes that arrived while the request was running. For the optimistic shopping-list order assert the state before `flush`, then flush an error and assert the fallback to the confirmed order (`shoppinglist.service.spec.ts`).
- **Error-message contract:** when a service checks `err.error` (`includes(...)`/`===`), use the exact backend message as the error body in the test. The backend pins the same text in an API spec; the list is in the Groovydoc of `spring-backend/.../controller/CartControllerApiSpec.groovy` (stopgap until error codes are decided).

**WebSocket and shopping lists**
- Handlers that change state (`AppComponent.subscribeToTopics`, `shoppinglist.page.ts`) are tested through the pure functions they call (`*-events.spec.ts`), not through the component. `WebSocketService` is tested with `src/testing/fake-stomp-client.ts` via the `STOMP_CLIENT_FACTORY` token.
- Shopping-list tests check state and HTTP, not the navigation to `server-unavailable` — keeps them valid once lists become offline-capable.

**Reference test:** [`src/app/domains/cartlist/service/cart.service.spec.ts`](src/app/domains/cartlist/service/cart.service.spec.ts).

## Conventions

- Constants and default/sentinel objects live in `src/app/constants/` (`default-values.ts` `INIT_VALUES`/`Init`, `recurrence-type.ts`). The "DEFAULT" group flag (`INIT_VALUES.DEFAULT`) marks the empty/placeholder active group and is checked before fetching data.
- Per-feature `model/` folders hold DTO interfaces; suffix `Dto` denotes a backend payload shape.
- New pages: generate as a lazy-loaded NgModule, add to `app-routing.module.ts` with `canLoad: [AuthGuard]` and `canActivate: [AuthGuard]` (pinned by `auth.guard.spec.ts`), and keep page-specific state in a root-provided signal service rather than the component.
