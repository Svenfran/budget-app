# Divvy (budget-app)

Mobile app for shared expenses within groups: members record purchases ("carts") by category, share shopping lists, and settle balances. Changes to groups, members and shopping lists sync live to other members via WebSocket.

| Path | Content |
|---|---|
| [`ionic-frontend/`](ionic-frontend) | Ionic 8 / Angular 19 / Capacitor 7 app (Android) |
| [`spring-backend/`](spring-backend) | Spring Boot 2.7 / Java 17 REST API + STOMP/SockJS WebSocket, Postgres via Liquibase |
| [`docs/`](docs) | Architecture notes, implementation plans, design sketches |
| `railway.json` | Backend deployment on Railway (`spring-backend/Dockerfile`, `prod` profile) |

## Prerequisites

- Java 17 (Maven wrapper included)
- Node.js + npm
- Docker (local Postgres and integration tests)
- Android Studio / Android SDK + `adb` for device builds

## Local setup

### Backend

1. Create `spring-backend/.env` (gitignored, never commit) with:
   ```properties
   DB_URL=jdbc:postgresql://localhost:5442/postgres
   DB_USERNAME=...
   DB_PASSWORD=...
   secret_key=...          # JWT signing key
   BREVO_API_KEY=...       # e-mail (verification / password reset)
   BREVO_SENDER=...
   BREVO_APP_NAME=...
   ```
2. Start Postgres and the backend:
   ```bash
   cd spring-backend
   docker compose up -d        # Postgres on host port 5442
   ./mvnw spring-boot:run      # 'dev' profile, http://0.0.0.0:9090, loads demo data
   ```

### Frontend

1. Point `ionic-frontend/src/config/config.ts` at your backend (LAN IP, port 9090). New origins must also be added to the backend's `app.cors.allowed-origins`.
2. Run:
   ```bash
   cd ionic-frontend
   npm install
   npm start                   # browser dev server (localhost:8100 via Ionic)
   npm run android:dev         # build + install Dev flavor on a connected device
   ```
   Release builds (`npm run android:prod`) need `android/keystore.properties` (copy from `keystore.properties.example`).

## Tests

```bash
cd spring-backend && ./mvnw test   # unit tests only, no Docker needed
cd spring-backend && ./mvnw verify # all tests incl. integration tests (Testcontainers, Docker must be running)
cd ionic-frontend && npm test      # Karma + Jasmine
cd ionic-frontend && npm run lint
```

## Deployments

The backend is deployed on Railway from `railway.json` with the `prod` profile; database settings come from Railway's `PG*` variables. The production app uses `src/config/config.prod.ts` as its backend URL.

## Further documentation

- [`CLAUDE.md`](CLAUDE.md) – how the projects fit together (REST, DTO contract, WebSocket topics, i18n) and working rules
- [`ionic-frontend/CLAUDE.md`](ionic-frontend/CLAUDE.md) – frontend architecture, build configuration, Android flavors
- [`spring-backend/CLAUDE.md`](spring-backend/CLAUDE.md) – backend architecture, profiles, Liquibase, testing
