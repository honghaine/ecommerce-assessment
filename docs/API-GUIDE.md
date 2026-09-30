# API guide — step by step (curl + Postman)

Walks through every feature in order: authentication → buying in a flash sale → seller configuration →
platform admin → warehouse sync. Each step has a short description, a copy-paste `curl`, and the expected result.

- **Postman:** import [`postman/FlashSale.postman_collection.json`](../postman/FlashSale.postman_collection.json) and
  [`postman/FlashSale.local.postman_environment.json`](../postman/FlashSale.local.postman_environment.json), select the
  environment, run folder **0. Setup**, then the other folders (tokens, ids, keys and dates are captured automatically).
- **Newman (CLI, no install):**
  ```bash
  docker run --rm --network flashsale_default -v "$PWD/postman":/etc/newman postman/newman:alpine \
    run FlashSale.postman_collection.json -e FlashSale.local.postman_environment.json \
    --env-var baseUrl=http://app:8080 --env-var warehouseApiKey=$(grep ^WAREHOUSE_API_KEY= .env | cut -d= -f2)
  ```
- **Swagger UI:** http://localhost:8080/swagger-ui.html

The curl blocks below are meant to be run **in order in one terminal** (they reuse shell variables). They need `curl`
and `jq`, and the stack running: `docker compose up -d --build`.

---

## 0. Setup

```bash
BASE=http://localhost:8080/api/v1
PASSWORD=Secret123
json() { curl -s -H 'Content-Type: application/json' "$@"; }
login() { json -X POST $BASE/auth/login -d "{\"identifier\":\"$1\",\"password\":\"$PASSWORD\"}" | jq -r .accessToken; }

BUYER="buyer$(printf '%04d' $((RANDOM % 200 + 1)))@demo.flashsale.dev"   # random demo buyer
BUYER_TOKEN=$(login "$BUYER")
SELLER_TOKEN=$(login seller.vn@flashsale.dev)
ADMIN_TOKEN=$(login admin.vn@flashsale.dev)
echo "buyer=$BUYER tokens: ${BUYER_TOKEN:0:12}… ${SELLER_TOKEN:0:12}… ${ADMIN_TOKEN:0:12}…"
```

---

## 1. Authentication

### 1.1 Register
One API for email **or** phone (`+84912345678`, international format). Creates an unverified buyer + wallet and sends a
6-digit OTP (mocked). The answer is always `202` with the same message — whether or not the account exists.
```bash
EMAIL="user-$(date +%s)@example.com"
json -X POST $BASE/auth/register -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"region\":\"VN\"}"; echo
```
Expected: `202 {"message":"If the identifier can be registered, a verification code has been sent"}`

### 1.2 Read the OTP (mocked delivery)
```bash
OTP=$(docker compose exec -T redis redis-cli HGET "otp:register:$EMAIL" code | tr -d '\r')
echo "OTP=$OTP"          # or: docker compose logs app | grep MOCK
```

### 1.3 Verify OTP
Single use, valid 5 minutes, invalidated after 5 wrong attempts.
```bash
json -X POST $BASE/auth/otp/verify -d "{\"identifier\":\"$EMAIL\",\"code\":\"$OTP\"}"; echo
```
Expected: `200 {"message":"Account verified"}` (logging in before this step → `403 ACCOUNT_NOT_VERIFIED`).

### 1.4 Login
Returns a 15-minute JWT access token and a 7-day rotating refresh token. Unknown user and wrong password both return
the same `401 INVALID_CREDENTIALS`.
```bash
TOKENS=$(json -X POST $BASE/auth/login -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
ACCESS=$(echo "$TOKENS" | jq -r .accessToken); REFRESH=$(echo "$TOKENS" | jq -r .refreshToken)
echo "$TOKENS" | jq '{tokenType, expiresIn, refreshExpiresIn}'
```

### 1.5 Current user
```bash
curl -s $BASE/users/me -H "Authorization: Bearer $ACCESS" | jq
```
Expected: profile with masked email, e.g. `"email": "u*****************2@example.com"`.

### 1.6 Refresh (rotation)
The old refresh token stops working; re-using it later revokes every session of the user.
```bash
TOKENS=$(json -X POST $BASE/auth/refresh -d "{\"refreshToken\":\"$REFRESH\"}")
ACCESS=$(echo "$TOKENS" | jq -r .accessToken); REFRESH=$(echo "$TOKENS" | jq -r .refreshToken)
```

### 1.7 Logout
Blacklists the access token (all instances, until it expires) and revokes the refresh token.
```bash
json -X POST $BASE/auth/logout -H "Authorization: Bearer $ACCESS" -d "{\"refreshToken\":\"$REFRESH\"}" -o /dev/null -w "logout %{http_code}\n"
curl -s -o /dev/null -w "me after logout %{http_code}\n" $BASE/users/me -H "Authorization: Bearer $ACCESS"
```
Expected: `logout 204`, `me after logout 401`.

---

## 2. Flash sale — buyer

### 2.1 Products on flash sale now
Public (`?region=`); with a Bearer token the token's region is used. Cached ~2 s.
```bash
CURRENT=$(curl -s "$BASE/flash-sales/current?region=VN")
echo "$CURRENT" | jq '{serverTime, region, currency, sessions: [.sessions[] | {name, items: [.items[] | {itemId, sku, amount, remaining}]}]}'
ITEM=$(echo "$CURRENT" | jq '[.sessions[].items[] | select(.remaining > 0)][0].itemId'); echo "ITEM=$ITEM"
```

### 2.2 Purchase
One unit, only while the slot is live, **1 flash-sale product per user per day**. `Idempotency-Key` makes retries safe.
```bash
KEY=$(uuidgen)
json -X POST $BASE/flash-sales/items/$ITEM/purchase -H "Authorization: Bearer $BUYER_TOKEN" -H "Idempotency-Key: $KEY" -w "\n%{http_code}\n"
```
Expected: `201` `{orderId, itemId, productId, amount, currency, status: "PAID", purchasedAt, balance}`
(or `409 ALREADY_PURCHASED_TODAY` if this random demo buyer already bought today — rerun step 0).

### 2.3 Retry with the same key → replay
```bash
json -X POST $BASE/flash-sales/items/$ITEM/purchase -H "Authorization: Bearer $BUYER_TOKEN" -H "Idempotency-Key: $KEY" -o /dev/null -w "replay %{http_code}\n"
```
Expected: `replay 200` — the original order, no second charge.

### 2.4 Second purchase the same day → rejected
```bash
json -X POST $BASE/flash-sales/items/$ITEM/purchase -H "Authorization: Bearer $BUYER_TOKEN" -H "Idempotency-Key: $(uuidgen)" | jq -r .code
```
Expected: `ALREADY_PURCHASED_TODAY`. Other possible errors: `SOLD_OUT`, `FLASH_SALE_NOT_ACTIVE`, `INSUFFICIENT_BALANCE`.

---

## 3. Seller — products, stock, flash-sale rules

### 3.1 Region schedule (rule start times must match the windows)
```bash
curl -s $BASE/seller/flash-sales/config -H "Authorization: Bearer $SELLER_TOKEN" | jq '{slotMinutes, windowsPerDay, activeDays}'
```
Expected: `60`, `24`, all 7 days.

### 3.2 Create a product with stock
```bash
SKU="VN-HP-$(date +%s)"
PRODUCT=$(json -X POST $BASE/seller/products -H "Authorization: Bearer $SELLER_TOKEN" \
  -d "{\"sku\":\"$SKU\",\"name\":\"Headphones\",\"price\":4000000,\"stock\":500}" | jq .id); echo "PRODUCT=$PRODUCT"
```

### 3.3 Restock (idempotent)
```bash
RKEY=$(uuidgen)
for i in 1 2; do json -X POST $BASE/seller/products/$PRODUCT/restock -H "Authorization: Bearer $SELLER_TOKEN" \
  -H "Idempotency-Key: $RKEY" -d '{"quantity":100}' | jq -c '{total, available, reserved}'; done
```
Expected: `{"total":600,...}` twice — the second call does not add stock again.

### 3.4 Schedule the product: slot time × day of week
"Put it in the **21:00** window **tomorrow's weekday**, 2,500,000, 30 per slot." Occurrences are generated immediately
and the quota is reserved from stock (`available → reserved`).
```bash
TOMORROW=$(TZ=Asia/Ho_Chi_Minh date -v+1d +%F 2>/dev/null || TZ=Asia/Ho_Chi_Minh date -d tomorrow +%F)
DOW=$(TZ=Asia/Ho_Chi_Minh date -v+1d +%A 2>/dev/null || TZ=Asia/Ho_Chi_Minh date -d tomorrow +%A); DOW=$(echo $DOW | tr a-z A-Z)
RULE=$(json -X POST $BASE/seller/flash-sales/rules -H "Authorization: Bearer $SELLER_TOKEN" \
  -d "{\"productId\":$PRODUCT,\"slotStartTime\":\"21:00\",\"daysOfWeek\":[\"$DOW\"],\"salePrice\":2500000,\"quota\":30}" | jq .id)
echo "RULE=$RULE ($DOW 21:00)"
curl -s "$BASE/seller/flash-sales/items?date=$TOMORROW" -H "Authorization: Bearer $SELLER_TOKEN" | jq -c ".[] | select(.ruleId == $RULE) | {itemId, slotName, quota, status}"
```
Validation errors: `SLOT_TIME_NOT_ALIGNED` (e.g. `12:30`), `INVALID_SALE_PRICE` (≥ list price), `RULE_ALREADY_EXISTS`,
`PRODUCT_NOT_FOUND` (not your product).

### 3.5 Change, pause, resume the rule
Not-yet-started occurrences are regenerated; running slots are never touched.
```bash
json -X PUT $BASE/seller/flash-sales/rules/$RULE -H "Authorization: Bearer $SELLER_TOKEN" \
  -d "{\"productId\":$PRODUCT,\"slotStartTime\":\"21:00\",\"daysOfWeek\":[\"$DOW\"],\"salePrice\":2500000,\"quota\":50}" | jq -c '{quota, status}'
json -X POST $BASE/seller/flash-sales/rules/$RULE/pause  -H "Authorization: Bearer $SELLER_TOKEN" | jq -c '{status}'
json -X POST $BASE/seller/flash-sales/rules/$RULE/resume -H "Authorization: Bearer $SELLER_TOKEN" | jq -c '{status}'
```

### 3.6 Withdraw one occurrence
```bash
OCC=$(curl -s "$BASE/seller/flash-sales/items?date=$TOMORROW" -H "Authorization: Bearer $SELLER_TOKEN" | jq "[.[] | select(.ruleId == $RULE and .status == \"APPROVED\")][0].itemId")
json -X POST $BASE/seller/flash-sales/items/$OCC/withdraw -H "Authorization: Bearer $SELLER_TOKEN" | jq -c '{itemId, status}'
```
Expected: `WITHDRAWN`; its reserved quota is back in `available`.

### 3.7 Edit the product (price / status)
Price or status changes sync to flash sales: `"status":"INACTIVE"` blocks purchases immediately and pauses all its rules.
```bash
json -X PATCH $BASE/seller/products/$PRODUCT -H "Authorization: Bearer $SELLER_TOKEN" -d '{"price":3900000}' | jq -c '{price, total, available, reserved}'
```

---

## 4. Platform admin

```bash
A=(-H "Authorization: Bearer $ADMIN_TOKEN")
curl -s $BASE/admin/flash-sales/config "${A[@]}" | jq -c '{slotMinutes, windowsPerDay, horizonDays}'          # schedule
curl -s "$BASE/admin/flash-sales/sessions?date=$TOMORROW" "${A[@]}" | jq '.[0] | {name, sellers}'          # slot, items by seller
curl -s -X POST $BASE/admin/flash-sales/generate "${A[@]}" | jq -c                                           # run generator now
curl -s $BASE/admin/inventory/audit "${A[@]}" | jq '{allConsistent, pendingEvents, failedEvents}'           # stock vs ledger
curl -s "$BASE/admin/outbox?status=FAILED" "${A[@]}" | jq length                                             # dead letters
```
Change the schedule (applies to slots generated from now on):
```bash
json -X PUT $BASE/admin/flash-sales/config "${A[@]}" \
  -d '{"enabled":true,"slotMinutes":60,"activeDays":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],"horizonDays":2}' | jq -c '{slotMinutes, horizonDays}'
```

---

## 5. Warehouse stock sync (API key)

The external warehouse pushes stock **deltas**; each `(source, eventId)` is applied at most once.
```bash
WKEY=$(grep ^WAREHOUSE_API_KEY= .env | cut -d= -f2); EV="rcv-$(date +%s)"
BODY="{\"source\":\"WMS-HCM\",\"events\":[{\"eventId\":\"$EV\",\"region\":\"VN\",\"sku\":\"$SKU\",\"delta\":50,\"reason\":\"RECEIVED\"}]}"
json -X POST $BASE/integrations/warehouse/stock-events -H "X-Api-Key: $WKEY" -d "$BODY" | jq -c '.results'
json -X POST $BASE/integrations/warehouse/stock-events -H "X-Api-Key: $WKEY" -d "$BODY" | jq -c '.results'   # re-send
```
Expected: first `APPLIED`, re-send `DUPLICATE` (`original: APPLIED`); a wrong key → `401`.

---

## 6. Watch it in Grafana (optional)

```bash
echo "TRACING_ENABLED=true" >> .env && docker compose --profile observability up -d
open http://localhost:3000        # FlashSale folder → "2. Flash sale live"
```
Re-run steps 2.2–2.4: the purchase results appear per result (`success`, `already_purchased_today`, …); open a line in
*Purchase logs* → `traceId` → the full trace (Redis gate + every SQL statement of the purchase transaction).
Metrics are served on the internal management port: `curl -s localhost:8081/actuator/prometheus | grep flashsale_`.

---

## Error format

Every error is RFC 7807 JSON with a stable `code` and a `correlationId` (also in the `X-Correlation-Id` header and logs):
```json
{ "type": "urn:flashsale:error:sold_out", "title": "Conflict", "status": 409,
  "detail": "Flash sale item is sold out", "code": "SOLD_OUT", "correlationId": "4f1c…" }
```
Validation errors list field names only (never the rejected values): `"invalidFields": ["password"]`.
