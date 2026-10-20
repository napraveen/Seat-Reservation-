import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const PnrLatency = new Trend('railflow_pnr_latency_ms');
const CacheHitRate = new Rate('railflow_pnr_cache_hit_rate');
const Errors = new Counter('railflow_pnr_errors');

export const options = {
  scenarios: {
    pnr_burst: {
      executor: 'ramping-vus',
      startVUs: 50,
      stages: [
        { duration: '20s', target: 500 },
        { duration: '1m', target: 2000 },
        { duration: '1m', target: 5000 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    'railflow_pnr_latency_ms': ['p(95)<40', 'p(99)<80'], // PNR Inquiry SLO: p99 < 80ms
    'http_req_failed': ['rate<0.005'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Sample PNRs seeded in database
const SAMPLE_PNRS = [
  '2847193850',
  '4928172948',
  '6291847192',
  '8392018472',
  '1928475829',
  '7391827462',
  '5928174829',
  '3847291847'
];

export default function () {
  const pnr = SAMPLE_PNRS[Math.floor(Math.random() * SAMPLE_PNRS.length)];
  const url = `${BASE_URL}/api/v1/pnr/${pnr}`;

  const res = http.get(url, {
    headers: {
      'Accept': 'application/json',
      'X-Request-Source': 'pnr-storm-runner',
    },
  });

  PnrLatency.add(res.timings.duration);

  const passed = check(res, {
    'status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    'has valid PNR format if 200': (r) => r.status !== 200 || r.json('pnr') === pnr,
  });

  if (!passed) {
    Errors.add(1);
  }

  const cacheHeader = res.headers['X-Cache-Status'] || res.headers['x-cache-status'];
  CacheHitRate.add(cacheHeader === 'HIT');

  sleep(0.02);
}
