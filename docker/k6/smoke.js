// Placeholder smoke test — real flash-sale load test added with the purchase flow.
import http from 'k6/http';
import { check } from 'k6';

export const options = { vus: 10, duration: '10s' };

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const res = http.get(`${BASE_URL}/actuator/health`);
  check(res, { 'status is 200': (r) => r.status === 200 });
}
