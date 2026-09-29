# FlashSale Service

Backend service for user authentication and flash sales — Java 25, Spring Boot 4.1, PostgreSQL 16, Redis 7.

> Status: authentication (2.1) and flash-sale buyer flow (2.2) implemented; inventory sync (2.3) next.
> Design notes: [brainstorm.md](brainstorm.md).

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
RATE_LIMIT_LOGIN_IP=100000 docker compose --profile loadtest run --rm k6   # k6 flash-sale load test
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

## Flash Sale API

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/flash-sales/current?region=VN` | public | Live slots + items. With a Bearer token the token's region is used |
| POST | `/api/v1/flash-sales/items/{itemId}/purchase` | Bearer (buyer) | Header `Idempotency-Key` required. 201 = bought, 200 = replay of the same key |

Errors: `404 FLASH_SALE_ITEM_NOT_FOUND`, `409 FLASH_SALE_NOT_ACTIVE / SOLD_OUT / ALREADY_PURCHASED_TODAY / IDEMPOTENCY_KEY_REUSED`, `422 INSUFFICIENT_BALANCE`, `429 TOO_MANY_REQUESTS`.

### Demo data (`SEED_ENABLED=true`, default in Docker)
All flash-sale configuration is DB rows; the seeder only inserts them (idempotent, re-runs hourly so tomorrow's slots always exist).

| What | Value |
|---|---|
| Buyers | `buyer0001@demo.flashsale.dev` … `buyer0200@demo.flashsale.dev`, password `Secret123`, balance 100,000,000 VND |
| Seller / platform admin | `seller.vn@flashsale.dev`, `admin.vn@flashsale.dev` (cannot purchase) |
| Slots | 6 × 4h per VN day (00–04, 04–08, …, 20–24), today + tomorrow |
| Items per slot | 3 items, quota 5 / 20 / 100, 50–70 % of list price |

### How "no oversell" and "1 product per user per day" are guaranteed
1. **Redis gate** (Lua, atomic): rejects "already bought today" / "sold out" before touching the DB — sheds load.
2. **One DB transaction** — the source of truth:
   `UPDATE flash_sale_items SET sold = sold + 1 WHERE … sold < quota AND now() in slot` →
   `UPDATE wallets … WHERE balance >= amount` → insert order (`UNIQUE (user_id, idempotency_key)`) →
   insert `user_daily_purchases` (**PK (user_id, purchase_date)**) → ledger + `ORDER_CREATED` outbox event.
3. If the DB rejects, the Redis gate is compensated; a reconciler (ShedLock, every 15 s) rebuilds Redis stock from the DB.

### Load / concurrency test (k6)
```bash
RATE_LIMIT_LOGIN_IP=100000 docker compose --profile loadtest run --rm k6
```
Phase 1: 200 buyers buy the quota-5 item at the same instant → exactly 5 orders. Phase 2: 500 req/s for 30 s
(80 % `GET current`, 20 % purchase). Measured locally: **0 errors, p95 ≈ 2.3 ms at 500 req/s**; DB check after the
burst: `sold = 5`, 5 orders, no user with 2 purchases on a day, debits = order totals.

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
├── catalog/         products
├── inventory/       stock (available / reserved) + movement ledger
├── flashsale/       slots, items, listing, purchase, Redis stock gate (support/), reconciler (scheduler/)
├── order/           orders
├── outbox/          domain event outbox (publisher; poller + consumers come with 2.3)
├── seed/            idempotent demo data
├── common/          errors, rate limiting, logging, security handlers
└── config/          security, scheduling (ShedLock), OpenAPI
```
