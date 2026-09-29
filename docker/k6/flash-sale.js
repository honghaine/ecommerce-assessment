// Flash-sale load & concurrency check.
//
//   RATE_LIMIT_LOGIN_IP=100000 docker compose up -d app      # allow k6 to log in many buyers
//   docker compose --profile loadtest run --rm k6            # runs this script
//
// Phase 1 (burst):  BUYERS demo buyers hit "purchase" on the smallest-quota live item at the same instant.
//                   Expect exactly `quota` 201s, the rest 409 SOLD_OUT — no oversell.
// Phase 2 (load):   RATE req/s for DURATION: 80% GET current, 20% purchase attempts (mostly 409s / 429s).
import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const REGION = __ENV.REGION || 'VN';
const BUYERS = parseInt(__ENV.BUYERS || '200', 10);
const RATE = parseInt(__ENV.RATE || '500', 10);
const DURATION = __ENV.DURATION || '30s';
const PASSWORD = __ENV.PASSWORD || 'Secret123';

const purchased = new Counter('purchase_201');
const soldOut = new Counter('purchase_409_sold_out');
const alreadyToday = new Counter('purchase_409_already_today');
const otherStatus = new Counter('purchase_other');

http.setResponseCallback(http.expectedStatuses(200, 201, 409, 429));

export const options = {
  setupTimeout: '3m',
  scenarios: {
    burst: {
      executor: 'per-vu-iterations', vus: BUYERS, iterations: 1, maxDuration: '1m', exec: 'burst',
    },
    load: {
      executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 200, maxVUs: 1000, startTime: '20s', exec: 'load',
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{scenario:load}': ['p(95)<500'],
  },
};

function json(body) {
  return { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

function uuid() {
  return 'k6-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 12);
}

export function setup() {
  const current = http.get(`${BASE_URL}/api/v1/flash-sales/current?region=${REGION}`).json();
  const items = (current.sessions || []).flatMap((s) => s.items);
  if (items.length === 0) fail('no live flash-sale items — is SEED_ENABLED=true?');
  const target = items.reduce((a, b) => (b.remaining < a.remaining ? b : a));
  console.log(`target item ${target.itemId} (${target.sku}) quota=${target.quota} remaining=${target.remaining}`);

  const tokens = [];
  for (let start = 1; start <= BUYERS; start += 50) {
    const batch = [];
    for (let i = start; i < Math.min(start + 50, BUYERS + 1); i++) {
      const email = `buyer${String(i).padStart(4, '0')}@demo.flashsale.dev`;
      batch.push(['POST', `${BASE_URL}/api/v1/auth/login`, json({ identifier: email, password: PASSWORD }).body,
        { headers: { 'Content-Type': 'application/json' } }]);
    }
    http.batch(batch).forEach((res) => {
      if (res.status !== 200) fail(`login failed: ${res.status} ${res.body} (raise RATE_LIMIT_LOGIN_IP)`);
      tokens.push(res.json('accessToken'));
    });
  }
  return { itemId: target.itemId, remainingBefore: target.remaining, tokens };
}

function purchase(data, token) {
  const res = http.post(`${BASE_URL}/api/v1/flash-sales/items/${data.itemId}/purchase`, null, {
    headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': uuid() },
    tags: { name: 'purchase' },
  });
  if (res.status === 201) purchased.add(1);
  else if (res.status === 409 && res.body.includes('SOLD_OUT')) soldOut.add(1);
  else if (res.status === 409 && res.body.includes('ALREADY_PURCHASED_TODAY')) alreadyToday.add(1);
  else otherStatus.add(1);
  logUnexpected(res);
  return res;
}

function logUnexpected(res) {
  if (![200, 201, 409, 429].includes(res.status)) {
    console.warn(`unexpected ${res.status} ${res.request.method} ${res.url}: ${String(res.body).slice(0, 200)} ${res.error}`);
  }
}

export function burst(data) {
  // unique 0..BUYERS-1 per burst iteration (VU ids are shared across scenarios, so not usable here)
  const token = data.tokens[exec.scenario.iterationInTest];
  const res = purchase(data, token);
  check(res, { 'burst: 201 or 409': (r) => r.status === 201 || r.status === 409 });
}

export function load(data) {
  if (Math.random() < 0.8) {
    const res = http.get(`${BASE_URL}/api/v1/flash-sales/current?region=${REGION}`, { tags: { name: 'current' } });
    check(res, { 'current: 200': (r) => r.status === 200 });
    logUnexpected(res);
  } else {
    purchase(data, data.tokens[Math.floor(Math.random() * data.tokens.length)]);
  }
}

export function teardown(data) {
  const current = http.get(`${BASE_URL}/api/v1/flash-sales/current?region=${REGION}`).json();
  const item = (current.sessions || []).flatMap((s) => s.items).find((i) => i.itemId === data.itemId);
  console.log(`item ${data.itemId}: remaining before=${data.remainingBefore}, after=${item ? item.remaining : 'n/a'} ` +
    '(verify in DB: SELECT quota, sold FROM flash_sale_items WHERE id = ' + data.itemId + ')');
}
