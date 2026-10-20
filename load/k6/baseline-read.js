import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom Metrics
const ReadLatency = new Trend('railflow_read_latency_ms');
const CacheHitRate = new Rate('railflow_cache_hit_rate');
const FailedReads = new Counter('railflow_failed_reads');

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'ramping-arrival-rate',
      startRate: 1000,
      timeUnit: '1s',
      preAllocatedVUs: 500,
      maxVUs: 5000,
      stages: [
        { duration: '30s', target: 5000 },   // Warm up to 5k rps
        { duration: '1m', target: 20000 },   // Ramp to 20k rps
        { duration: '2m', target: 50000 },   // Sustained 50k rps
        { duration: '30s', target: 0 },       // Cool down
      ],
    },
  },
  thresholds: {
    'railflow_read_latency_ms': ['p(95)<30', 'p(99)<50'], // SLO: p99 < 50ms
    'http_req_failed': ['rate<0.001'],                     // 99.9% availability
    'railflow_cache_hit_rate': ['rate>0.95'],              // >95% cache hit target
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const TRAIN_QUERY_PARAMS = [
  { from: 'NDLS', to: 'BCT', date: '2026-10-22', quota: 'GN' },
  { from: 'NDLS', to: 'HWH', date: '2026-10-22', quota: 'GN' },
  { from: 'SBC', to: 'MAS', date: '2026-10-22', quota: 'GN' },
  { from: 'NDLS', to: 'BCT', date: '2026-10-22', quota: 'TQ' },
  { from: 'CSMT', to: 'NGP', date: '2026-10-22', quota: 'GN' },
];

export default function () {
  const query = TRAIN_QUERY_PARAMS[Math.floor(Math.random() * TRAIN_QUERY_PARAMS.length)];
  const url = `${BASE_URL}/api/v1/availability?fromStation=${query.from}&toStation=${query.to}&journeyDate=${query.date}&quota=${query.quota}`;

  const res = http.get(url, {
    headers: {
      'Accept': 'application/json',
      'X-Client-Id': `k6-runner-${__VU}`,
    },
  });

  ReadLatency.add(res.timings.duration);

  const isSuccess = check(res, {
    'status is 200': (r) => r.status === 200,
    'has bounded staleness as_of': (r) => r.json('asOf') !== undefined,
    'has valid classes': (r) => Array.isArray(r.json('classes')),
  });

  if (!isSuccess) {
    FailedReads.add(1);
  }

  // Check header for cache hit
  const cacheHeader = res.headers['X-Cache-Lookup'] || res.headers['x-cache-lookup'];
  CacheHitRate.add(cacheHeader === 'HIT');

  sleep(0.01);
}
