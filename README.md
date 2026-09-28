# FlashSale Service

Backend service for user authentication and flash sales — Java 25, Spring Boot 4.1, PostgreSQL 16, Redis 7.

> Status: project skeleton. Business logic not implemented yet. Design notes: [brainstorm.md](brainstorm.md).

## Run with Docker (only Docker required)

```bash
cp .env.example .env              # optional — defaults work out of the box
docker compose up -d --build      # app + postgres + redis
```

| URL | |
|---|---|
| http://localhost:8080/swagger-ui.html | API docs |
| http://localhost:8080/actuator/health | Health |
| http://localhost:8080/actuator/prometheus | Metrics (authenticated) |

```bash
docker compose --profile scale up -d --build      # 2 app instances behind nginx → http://localhost:8088
docker compose --profile loadtest run --rm k6     # k6 load test
docker compose down                               # stop
docker compose down -v                            # stop + wipe data
```

## Dev tools (web UIs)

```bash
docker compose --profile tools up -d      # pgadmin + redisinsight (localhost only)
```

| Tool | URL | Login |
|---|---|---|
| pgAdmin 4 (PostgreSQL) | http://localhost:5050 | No login; server "flashsale (docker)" pre-registered, DB password `flashsale` |
| RedisInsight (Redis) | http://localhost:5540 | Database `flashsale-redis` pre-registered |

## Authentication API

Email vs phone is detected from `identifier` (phone must be international, e.g. `+84912345678`).

| Method | Path | Auth | Body |
|---|---|---|---|
| POST | `/api/v1/auth/register` | – | `{"identifier","password","region":"VN"}` |
| POST | `/api/v1/auth/otp/verify` | – | `{"identifier","code"}` |
| POST | `/api/v1/auth/otp/resend` | – | `{"identifier"}` |
| POST | `/api/v1/auth/login` | – | `{"identifier","password"}` |
| POST | `/api/v1/auth/refresh` | – | `{"refreshToken"}` |
| POST | `/api/v1/auth/logout` | Bearer | `{"refreshToken"}` (optional) |
| GET | `/api/v1/users/me` | Bearer | – |

OTP delivery is mocked: in Docker the code is logged (`NOTIFICATION_MOCK_LOG_CONTENT=true`):

```bash
docker compose logs app | grep "MOCK"
```

## Run locally (JDK 25)

```bash
docker compose up -d postgres redis
export JWT_SECRET=dev-only-jwt-secret-change-me-0123456789abcdef
export OTP_SECRET=dev-only-otp-secret-change-me-0123456789abcdef
./mvnw spring-boot:run
```

Or start with throwaway containers via Testcontainers: run `TestFlashsaleServiceApplication` from `src/test`.

## Test

```bash
./mvnw verify      # needs Docker running (Testcontainers: PostgreSQL + Redis)
```

## Project layout

Feature modules; each module is layered the same way:

```
src/main/java/com/flashsale/
├── auth/            register / login / logout, OTP, JWT
│   ├── controller/  REST endpoints
│   ├── dto/         request / response records
│   ├── entity/      JPA entities
│   ├── repository/  Spring Data repositories
│   ├── service/     service interfaces
│   │   └── impl/    service implementations
│   ├── config/      module properties / beans
│   ├── model/       internal value types (not persisted, not exposed)
│   └── support/     helpers (identifier parsing, hashing, JWT blacklist)
├── user/            users, wallets, /users/me
├── notification/    notification outbox, mock sender, dispatcher (scheduler/)
├── region/          region config → timezone / currency
├── catalog/ inventory/ flashsale/ order/ outbox/   (next)
├── common/          errors, rate limiting, logging, security handlers
└── config/          security, scheduling (ShedLock), OpenAPI
```
