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

**Module structure.** Classic Angular NgModule app (not standalone components — `AppComponent` and most pages set `standalone: false`). Every page is its own lazy-loaded feature module wired in `app-routing.module.ts`, guarded by `AuthGuard` (`canLoad` + `canActivate`: `canLoad` only runs when a module is first loaded, `canActivate` also covers returning to an already loaded page, e.g. browser back after logout). The main shell is `domains/tabs` (`domains-routing.module.ts`) with child tabs `overview`, `cartlist`, `shoppinglist`. The `cartlist/new-edit/:id` route is the add/edit-purchase form (`cartlist/new-edit` without id = add, `?duplicateOf=<cartId>` = add pre-filled from an existing cart, see below).

**State management = Angular signals in singleton services.** There is no NgRx. Root-provided services own `WritableSignal` state that components read directly:
- `GroupService` — `activeGroup`, `groupsSideNav`, `groupOverviewList`, `groupMembers`, `hasNoGroups`. The **active group** is the central pivot of the whole app; most data is fetched per `activeGroup().id` and persisted to device storage under key `ACTIVE_GROUP`.
- `CartService` (`domains/cartlist/service`) — `cartList`, plus `computed` `sum`/`count`.
- `CategoryService`, `HealthCheckService` (`backendStatus`), etc.

Cross-component "something changed, refetch" signaling uses an incrementing counter signal (e.g. `cartUpdated`, `memberUpdated`) bumped via `triggerUpdate()` and watched with `effect()`.

**Mutations update state after success.** Service write methods (e.g. `GroupService.addGroup/updateGroup/deleteGroup`, `CartService.addCart`, `ShoppinglistService.addShoppingList`) fire the HTTP call and update the signals from the response in `next`; on error they leave the state as it is and only report it (`CartService` shows an error alert, the shopping-list service a toast — except for checking off an item, which stays silent with the future offline story in mind). There is deliberately no snapshot restore: the state hasn't changed before the response, so a restore would undo nothing but could discard changes that arrived meanwhile (live events, a concurrent successful request). The one optimistic update is the shopping-list order (see Real-time updates).

**Auth (DIV-65).** `AuthService` keeps the session in memory: refresh token + profile, and the access token, which is
never persisted and never emitted via `user`, so a refresh doesn't trigger reloads.

- `user` emits a `User` with profile data only (`id`, `name`, `email`, `sessionExpiresAt`).
- `login`/`register` start a session; saving runs in the background and the login doesn't wait for it.
- `autoLogin()` restores the session once per app run and does not read storage again after logout. On first start
  it removes the legacy `authData`/`localStorage['token']`.
- `getValidAccessToken()` returns the in-memory token or a refreshed one. `refresh()` is shared by all concurrent
  callers and has a 15 s timeout.
  - A 401 on refresh ends the session and navigates to `/auth`.
  - Network errors, 5xx and 429 keep the session.
- An auth generation counter drops late answers from an ended session.
- Storage access is serialized through `auth/session-store.ts`: no overlapping native calls, callers wait at most 5 s.
- `logout()` clears local state immediately and revokes on the server in the background. It does not navigate —
  manual logout paths navigate to `/auth` themselves.
- `updateUserName()` changes the profile only.

`AuthHttpInterceptorService` (registered as a multi `HTTP_INTERCEPTORS` in `app.module.ts`):
- sends `Accept-Language` and `X-Error-Format` on every request;
- while a session exists, also `Authorization` + `DeviceId` on `/api/**` (except `/api/auth/**`) and `/actuator/health`;
- on a 401, in order:
  1. if the login changed since sending → pass the error on;
  2. else if a newer token exists → retry once with it;
  3. else refresh once and retry.

**Real-time updates.** `WebSocketService` connects via STOMP over SockJS to `${baseUrl}/ws` (one client per logged-in user; before every (re)connect the service asks `AuthService.webSocketAuthorization()`, which returns `WsAuthResult` — `token`, `no-session` or `retry` — and never throws. On `retry` the service rebuilds the client itself with backoff 5/10/30/60 s; `reconnectNow()` is called on resume. `AppComponent.initUser()` disconnects on logout/user change). Topics are `/user/notification/<action>` (no userId) and are registered once in the service's registry via `subscribe(topic, handler, { owner, filterByActiveGroup })`; the service re-subscribes them after every reconnect and keeps them across `disconnect()`, `unsubscribeOwner(owner)` removes them. Registrations are split by feature:
- `AppComponent.subscribeToTopics()` — group update/delete, member add/remove, owner change, and the global `/notification/health` topic.
- `domains/shoppinglist/shoppinglist.page.ts` — shopping list add/update/delete, shopping item add/update/delete/delete-all and the order topics `reorder-lists`/`reorder-items`. While a drag is active, `ShoppinglistService` holds back every asynchronous change of the lists (these WebSocket updates as well as GET, REST and reorder responses; `holdUpdates`/`releaseUpdates`/`applyOrDefer`) and applies them after the drop; held-back changes are dropped after a group/session change.

Incoming messages are parsed centrally (handler errors are logged, never thrown) and mutate the same service signals via pure functions (`service/group-events.ts`, `domains/shoppinglist/shoppinglist-events.ts`), keeping all open views in sync. With `filterByActiveGroup`, messages whose `groupId` ≠ the active group are dropped (list/item topics).

**Shopping-list order (DIV-1).** Lists and items are reordered by drag & drop (Angular CDK, start after touch-and-hold via `cdkDragStartDelay`). `ShoppinglistService` keeps a confirmed order per scope (list order of the group, item order per list: server revision + full id order) in pure functions in `domains/shoppinglist/shoppinglist-order.ts`: a move is shown immediately, only a newer revision (PUT response, WebSocket event, GET with ≥) replaces the confirmed order, and once no own request is pending the visible order falls back to the confirmed one — on error with a toast and a silent refetch. A context generation (active group + `session()`) discards responses that arrive after a switch, including logout without reload. Page specifics: the page scrolls in its own `div.ion-content-scroll-host` with `cdkScrollable` (`ion-content [scrollY]="false"`, also used by `ion-refresher`) so the CDK can auto-scroll; list headers override the CDK's `touch-action: none` with `pan-y` so they still scroll; lists use their own `*cdkDragPreview`/`*cdkDragPlaceholder`, because the CDK's clone of an `ion-input` breaks the drag start.

**Resync.** After a reconnect (`WebSocketService.reconnected$`) or a resume after > 30 s in the background, `AppComponent.resyncLiveData()` silently re-fetches groups, overview, members and the shopping lists of the active group (`LoadOptions { silent, isCurrent }`: no clearing, errors keep the old state, stale answers from an ended login session or another group are dropped), throttled by `service/resync-throttle.ts` (max. once per 60 s, only while connected). The tab load effects track `GroupService.activeGroupId()` and `session()` (not `activeGroup()`), so a rename doesn't reload a tab, but a group change or a new login session does.

**Statistics page (DIV-63).** `statistics/` is a top-level page (side menu, own back navigation, not a tab) backed by `statistics/service/statistics.service.ts`: one read-only request per filter (`GET /api/statistics`, group or all groups, `MONTH`/`YEAR`/`ALL`). It is a pure read state, so a new request clears the old data first and an error leaves no data (unlike mutations). A generation counter drops stale answers and answers from an ended `session()`; the filter is kept per login session. `groupsChanged()` (fed from `groupsSideNav`) reloads when a group disappears and falls back to "all groups". Display logic (donut segments, max. 8 + "other", category rows, labels) lives in pure functions in `statistics-calc.ts`. One answer carries both perspectives (DIV-72): the personal top-level fields and `groupView` (all carts of the selected groups, plus `cartCount`/`averagePerCart`/`memberCount`). The perspective (`PERSONAL`/`GROUP`) is a page-local signal in `StatisticsPage`, not part of the filter: switching sends no request and keeps group and period, and `ionViewWillEnter` resets it to `PERSONAL`. `perspectiveView(dto, perspective)` picks timeline, categories and the empty state for the charts. Charts use **Chart.js 4** directly (no Angular wrapper), registered in `components/chart-setup.ts` with only the needed parts, so it stays in the lazy statistics chunk; colors come from the Ionic theme variables via `chartTheme()`, and `ChartThemeService` re-reads them when `body[color-theme]` changes (canvas colors don't follow CSS on their own).

**Add/edit purchase form (DIV-73).** `domains/cartlist/new-edit-cart/` serves add, edit and duplicate. Duplicating (sliding action "Duplicate" in the cart list, only on the user's own non-settlement carts — same ownership check as edit/delete) is a plain *add* pre-filled from a cart in `cartList()`: title, amount, description and category are copied by the pure `duplicate-cart.ts` (`duplicateFormValues`, `duplicateSource`), the date is today and recurrence `NONE`; nothing is created before saving and the source cart is never referenced (the API has no link to it). The form does **not** read the shared `CategoryService.categories` signal — older requests of other pages (e.g. a closed filter sheet) can still overwrite it — but keeps the result of its own request (`getCategoriesByGroup(group, onComplete)`, `null` on error) in `formCategories`; saving is blocked until that request succeeded. The form never offers the settlement category (`selectableCategories`, filtered by `CategoryDto.settlement`) and redirects back to the list when opened for a settlement cart. `onSubmit` rejects locally (categories not loaded, invalid form, unknown category) before setting `saving`, which blocks a second submit until the page is left.

**Purchase dates are calendar days (DIV-81).** `Cart.datePurchased` and `SettlementPaymentDto.datePurchased` are ISO day strings `YYYY-MM-DD` (backend `LocalDate`), sent and received without time or zone. The form converts between `DD.MM.YYYY` and `YYYY-MM-DD` as strings and parses with an explicit format (`moment(x, 'YYYY-MM-DD')`); local `Date`s (e.g. for the next-execution preview) are built from local midnight. Never use `new Date('YYYY-MM-DD')` for a business day — that is UTC midnight and the previous day west of UTC.

**Settlement payments (DIV-74).** A settlement cart is recognized by `Cart.settlementId` (both carts of a payment share it), the settlement category by `CategoryDto.settlement` — never by name. Settlements are created, edited (direction toggle "paid"/"received", pure mapping in `settlement-sheet/settlement-form.ts`) and deleted only via the settlement sheet and `CartService.add/update/deleteSettlementPayment`, which update both carts in `cartList`/`initCartList` after success (`isCurrent` drops stale answers; a 404 on delete only shows a toast). The category overview hides the settlement category; filter sheet, chip and labels show it in the app language via `service/category-label.ts`. After every successful change (`cartUpdated`) `CartlistPage` reapplies the active filter with the pure `applyCartFilter` (`filter-sheet/cart-filter-form.ts`).

**Former and deleted members as settlement counterpart (DIV-109).** The settlement sheet loads the group's total balances once (`OverviewService.loadUserBalances`: read-only `GET /api/spendings/{groupId}`, doesn't touch the overview signals, `null` on error; answers after closing the sheet or a group/session change are dropped) and offers, after the current members, everyone who is neither me, the owner nor a current member and has a balance ≠ 0 rounded to the cent (pure `formerSettlementParties` in `settlement-form.ts`; "current" comes from the member list of the same group, never from the name marker), labeled "(former)" with the balance in the group currency. Deleted users (`DELETED` in carts and balances) show the `global.user.deleted` label via `displayParty` — also for the edit-mode fallback of the previous receiver, the fixed payer text and the membership error of an edit. The members sheet warns before leaving or removing a member with an open balance (`alerts.group.leave_open_balance` / `remove_member_open_balance`, not blocking; balance 0 or not loadable → the previous dialog; the sheet is locked via `saving` while the balance loads).

**Tab headers and cart list layout (DIV-79).** The tabs `overview`, `cartlist` and `shoppinglist` share a two-line title: page title, below it the active group as a button to `/groupoverview` (`.page-title*` classes in `global.scss`; own block elements in the `ion-title` slot, because its shadow wrapper sets `nowrap`/`overflow: hidden`). Names are shortened by CSS ellipsis only, never by `slice` in the template — the full text stays in the DOM. The cart list page puts its summary (label, count, sum) and filter row in a second `ion-toolbar` inside `ion-header`, laid out in the same `ion-col size-md="6" offset-md="3"` as the list, and scrolls in a stable `div.ion-content-scroll-host` with `cdkVirtualScrollingElement` (`ion-content [scrollY]="false"`, not under `*ngIf`, so `ion-refresher` stays attached): the CDK only re-measures on window resize, so a `ResizeObserver` on the host calls `checkViewportSize()` when the header height changes, and `isAtTop` comes from a template `(scroll)` handler (inside the Angular zone). In list rows, a single-line (`nowrap`) text needs a flex parent with `contain: inline-size` (see `.cart-repeat`): the CDK content wrapper is absolutely positioned and sizes to its content, so a long name would otherwise widen the whole list beyond the screen.

**Health / offline handling.** `HealthCheckService` queries `/actuator/health` on demand (login, resume, WebSocket close, reload button; 10 s timeout). The endpoint is protected: while a session exists the interceptor sends the access token, so a failed refresh due to the network counts as `DOWN`; the `/notification/health` WebSocket topic pushes `UP`/`DOWN`. On `DOWN` the app routes to `/server-unavailable`; on recovery it returns to the overview. When a user has no groups, the app routes to `/no-group`.

**Persistence.** `StorageService` wraps Capacitor `Preferences` (`setData/getData` with `{data}` wrapper, `setItem/getItem<T>` for `ACTIVE_GROUP`, `darkMode`). Auth data is **not** stored there or in `localStorage`: the session lives in `TokenStorage` (`auth/token-storage.ts`, `TOKEN_STORAGE` token) — `SecureTokenStorage` via `@aparajita/capacitor-secure-storage` (Android Keystore; unreadable entries are discarded) on native, `MemoryTokenStorage` in the browser (after a reload in `npm start` a new login is needed).

**i18n.** `@ngx-translate/core` with a `CustomTranslateLoader`. Languages `de`/`en`/`es` in `src/assets/i18n/*.json`; default `de`. `LanguageService` persists choice in `localStorage` (`appLang`) and is mirrored to the `Accept-Language` header by the interceptor. Use `translate.instant('key.path')` for alert/toast text rather than hardcoded strings.

**Theming.** Dark/light theme toggled via the `color-theme` body attribute (set with `Renderer2`), persisted as `darkMode` in Preferences, with native status/navigation bar colors set through Capacitor plugins on mobile.

## Testing

Karma + Jasmine. `npm run test:ci` (needs a local Chrome/Chromium and `src/config/config.ts`, see Environment — tests mock all HTTP, so the template's URL is fine) is the run that counts; `npm run lint` and `npm run build` belong to the verification as well. The test target loads `src/window-global-fix.ts` (needed by `sockjs-client`), configured in `angular.json` and `tsconfig.spec.json`.

**Test kinds**
- **Service tests** (the focus): `*.service.spec.ts` next to the service, `TestBed` + `provideServiceTesting()` + `HttpTestingController`, `afterEach(() => http.verify())`.
- **Plain unit tests** for pure functions (filters, sorting, date/sum logic): no `TestBed`. Move logic out of a service into a function when that makes it testable.
- **Component tests** only when the component has its own logic: `IonicModule.forRoot()` + `provideServiceTesting()` + service fakes.
- **Layout tests** (heights, widths, touch targets; e.g. `cartlist.page.spec.ts`): render the real template, give the fixture root the class `ion-page` (set by the router outlet in the app — without it `ion-content` doesn't get the height below the header) and a fixed size (`position: fixed` + width/height), then `await ionicReady(...)` and a `requestAnimationFrame`. `ion-col` breakpoints follow the window, not the fixture: temporarily widen the Karma iframe (`window.frameElement`) and dispatch `resize`. DOM tests still don't prove the look on a device; the manual check stays.

**No generated stubs.** There are no "should create" specs, and `angular.json` `schematics` keep `ng generate` from creating any (`skipTests: true` for the Angular schematics, `spec: false` for the Ionic `page`/`component`). Every spec is written on purpose.

**Shared helpers live only in `src/testing/`**
- `test-providers.ts`: `provideServiceTesting()` (mocked HTTP, empty router, `TranslateService` without loader — `instant()` returns the key, so alerts are asserted by key), `alertServiceSpy()` (`showErrorAlert`, `showToast`), `storageServiceFake()` (in-memory `StorageService`, stores JSON like the original — never let tests touch Capacitor Preferences) and `authServiceStub(user)` (`user`/`userIsAuthenticated` observables and `authGeneration`, plus spies `logout`/`updateUserName`/`login`/`register`/`getDeviceId`/`autoLogin`/`getValidAccessToken`/`refresh`/`webSocketAuthorization`/`hasSession`/`currentAccessToken`; `autoLogin` returns `false`, `getValidAccessToken` `'access-1'` by default), `tokenStorageFake({ manual? })` (records `save:<refreshToken>`/`clear`/`load`; with `manual: true` every access stays pending until `resolveNext()`/`rejectNext()`).
- `test-data.ts`: factories `aUser`, `aGroup`, `aCategory`, `aCart`, `aShoppingList`, `aShoppingItem` with `Partial<T>` overrides, unique ids and the fixed `REFERENCE_DATE` (2025-06-15T00:00Z), `REFERENCE_DAY` (`'2025-06-15'`, the ISO day `aCart` uses) and `REFERENCE_NOON` (2025-06-15 12:00 local time); `aLoggedInUser()` returns a `User` (profile only, no token) whose session ends 90 days after `REFERENCE_DATE`. When "today" must be 15.06. use `jasmine.clock().mockDate(REFERENCE_NOON)` (and `uninstall()` in `afterEach`). Never use the current date in tests.
- `ionic-ready.ts`: `ionicReady(root)` waits until all `ion-*` elements below `root` are defined and rendered (Ionic hydrates lazily in Karma) — call it before gestures or layout measurements, and again after the template adds new `ion-*` elements.
- Because the loader-less `TranslateService` doesn't interpolate, alert parameters (e.g. `{userName}`) are asserted on the call: `spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough()`, then `toHaveBeenCalledWith(key, params)`.
- Add a factory or helper only when a story needs it.
- Spec-local factories or consts used by a single spec file are fine (e.g. `listDto`/`itemDto` in `shoppinglist.service.spec.ts`, `statisticsDto` in `statistics.service.spec.ts`); once a second spec needs one, move it to `src/testing/`.

**What to assert**
- The request (URL, method, body), the signal state after success **and** after an error, the `triggerUpdate()` counters, and alert keys.
- Pin the actual behavior: services update state only after success, so after an error the state is unchanged — including changes that arrived while the request was running. For the optimistic shopping-list order assert the state before `flush`, then flush an error and assert the fallback to the confirmed order (`shoppinglist.service.spec.ts`).
- **Error-code contract (DIV-108):** services recognize error cases only by the backend code — `apiErrorCode(err)`/`apiErrorUserId(err)` from `service/api-error.ts` (constants in `API_ERROR_CODES`; `AuthHttpInterceptorService` sends `X-Error-Format: json` on every request) — never by `err.error` text. In tests, flush the JSON body `{ code, message, details? }` with the code the backend pins in its API spec (list in the Groovydoc of `spring-backend/.../controller/CartControllerApiSpec.groovy`), and cover bodies without a code (old text, `ProgressEvent`, `null`), which must fall back to the default behavior.

**WebSocket and shopping lists**
- Handlers that change state (`AppComponent.subscribeToTopics`, `shoppinglist.page.ts`) are tested through the pure functions they call (`*-events.spec.ts`), not through the component. `WebSocketService` is tested with `src/testing/fake-stomp-client.ts` via the `STOMP_CLIENT_FACTORY` token.
- Shopping-list tests check state and HTTP, not the navigation to `server-unavailable` — keeps them valid once lists become offline-capable.

**Time zones.** CI runs Karma twice, in `Europe/Berlin` and `America/Mexico_City` (steps "Tests (ChromeHeadless, …)" in `.github/workflows/ci.yml`). Specs must not depend on the device zone: a clock meant to be "today" uses local noon (`REFERENCE_NOON`, not the UTC-midnight `REFERENCE_DATE`), business days in test data and expectations are ISO days or `DD.MM.YYYY` strings, local dates are built with `new Date(y, m, d, …)`. The local `npm run test:ci` only covers the machine's zone.

**Reference test:** [`src/app/domains/cartlist/service/cart.service.spec.ts`](src/app/domains/cartlist/service/cart.service.spec.ts).

## Conventions

- Constants and default/sentinel objects live in `src/app/constants/` (`default-values.ts` `INIT_VALUES`/`Init`, `recurrence-type.ts`). The "DEFAULT" group flag (`INIT_VALUES.DEFAULT`) marks the empty/placeholder active group and is checked before fetching data.
- Per-feature `model/` folders hold DTO interfaces; suffix `Dto` denotes a backend payload shape.
- New pages: generate as a lazy-loaded NgModule, add to `app-routing.module.ts` with `canLoad: [AuthGuard]` and `canActivate: [AuthGuard]` (pinned by `auth.guard.spec.ts`), and keep page-specific state in a root-provided signal service rather than the component.
