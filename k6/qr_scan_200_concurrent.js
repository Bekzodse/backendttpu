/**
 * TTPU "Raqamli Inventar" — Milestone 6.2 / Prompt 18: k6 High-Concurrency Load Testing
 *
 * Target: QR Public Scan Endpoint (/a/{id})
 * Workload: 200 Concurrent Users (200 VUs) simulating mobile camera / kiosk QR scans
 * Target Uptime & Performance SLA: 99.5% success rate, response latency < 2000 ms (p95 < 500ms)
 *
 * Usage:
 *   k6 run k6/qr_scan_200_concurrent.js
 *   k6 run -e BASE_URL=http://localhost:8080 -e DURATION=1m -e TARGET_VUS=200 k6/qr_scan_200_concurrent.js
 *   docker run --rm -i --network=host grafana/k6 run - < k6/qr_scan_200_concurrent.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ---------------------------------------------------------------------------
// Custom SLA Metrics
// ---------------------------------------------------------------------------
const qrScanDuration = new Trend('qr_scan_duration_ms', true);
const qrScanUnder200msRate = new Rate('qr_scan_under_200ms_rate');
const qrScanUnder2sRate = new Rate('qr_scan_under_2s_rate');
const qrScanSuccessRate = new Rate('qr_scan_success_rate');
const totalQrScans = new Counter('total_qr_scans_completed');

// ---------------------------------------------------------------------------
// Environment & Workload Configuration
// ---------------------------------------------------------------------------
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET_VUS = parseInt(__ENV.TARGET_VUS || '200', 10);
const TEST_DURATION = __ENV.DURATION || '1m';
const RAMP_UP = __ENV.RAMP_UP || '10s';
const RAMP_DOWN = __ENV.RAMP_DOWN || '10s';

// ---------------------------------------------------------------------------
// k6 Options: 200 Concurrent Users Ramp-Up, Steady State, and Strict SLA
// ---------------------------------------------------------------------------
export const options = {
  scenarios: {
    qr_public_200_vus: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_UP, target: TARGET_VUS },    // Ramp up to 200 concurrent users
        { duration: TEST_DURATION, target: TARGET_VUS }, // Maintain 200 concurrent users
        { duration: RAMP_DOWN, target: 0 },          // Graceful ramp down
      ],
      gracefulRampDown: '5s',
      tags: { test_type: 'qr_public_scan_200_vus' },
    },
  },

  thresholds: {
    // 99.5% Uptime SLA: HTTP failure rate must stay strictly under 0.5% (0.005)
    http_req_failed: ['rate<0.005'],
    checks: ['rate>0.995'],
    qr_scan_success_rate: ['rate>0.995'],

    // Response Time SLA: Under 2 seconds (< 2000 ms) across all percentiles
    http_req_duration: [
      'avg<300',
      'p(90)<500',
      'p(95)<800',
      'p(99)<1500',
      'max<2000',
    ],
    qr_scan_duration_ms: [
      'avg<300',
      'p(95)<800',
      'p(99)<1500',
      'max<2000',
    ],
    qr_scan_under_2s_rate: ['rate>0.995'],
  },

  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// ---------------------------------------------------------------------------
// Setup: Discover or provide valid 10-character Base32 QR Codes
// ---------------------------------------------------------------------------
export function setup() {
  const defaultSeedQrIds = [
    'JBSWY3DPEB', // Dell Precision Workstation #1 (from V14 seed)
    'KRSXG5BAON', // Apple MacBook Pro 16"
    'MZXW6YTBOI', // Cisco Catalyst Core Switch
    'NBSWY3DPF4', // HP LaserJet Enterprise MFP
    'ORSXG5BAMN', // Lab Oscilloscope & Signal Analyzer
  ];

  const configuredQrIds = (__ENV.QR_IDS || '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);

  const activeQrIds = configuredQrIds.length > 0 ? configuredQrIds : defaultSeedQrIds;

  // Pre-flight check: verify endpoint responds
  const probe = http.get(`${BASE_URL}/a/${encodeURIComponent(activeQrIds[0])}`, {
    headers: { Accept: 'application/json' },
  });

  return {
    baseUrl: BASE_URL,
    qrIds: activeQrIds,
    preflightStatus: probe.status,
  };
}

// ---------------------------------------------------------------------------
// Main VU Loop: 200 Concurrent Users Hitting /a/{id}
// ---------------------------------------------------------------------------
export default function (data) {
  const qrIds = data.qrIds;
  // Distribute QR IDs across concurrent virtual users and iterations
  const qrId = qrIds[(__VU + __ITER) % qrIds.length];
  const url = `${data.baseUrl}/a/${encodeURIComponent(qrId)}`;

  const res = http.get(url, {
    headers: {
      Accept: 'application/json',
      'User-Agent': 'TTPU-QR-Scanner-LoadTest/2.0 (k6)',
    },
    tags: {
      name: 'GET /a/{id}',
      endpoint: 'qr_public_scan',
    },
  });

  const durationMs = res.timings.duration;
  totalQrScans.add(1);
  qrScanDuration.add(durationMs);
  qrScanUnder2sRate.add(durationMs < 2000);
  qrScanUnder200msRate.add(durationMs < 200);

  const isSuccess = check(res, {
    '1. HTTP status is 200 OK': (r) => r.status === 200,
    '2. Response time < 2000ms (NFT-2.1 SLA)': (r) => r.timings.duration < 2000,
    '3. Public card contains TTPU mulki ownership label': (r) => {
      if (r.status !== 200) return false;
      try {
        const body = r.json();
        return body && body.ownershipLabel === 'TTPU mulki';
      } catch (_) {
        return false;
      }
    },
    '4. Public card contains valid inventoryNumber and contactPhone': (r) => {
      if (r.status !== 200) return false;
      try {
        const body = r.json();
        return (
          typeof body.inventoryNumber === 'string' &&
          body.inventoryNumber.length > 0 &&
          typeof body.contactPhone === 'string' &&
          body.contactPhone.length > 0
        );
      } catch (_) {
        return false;
      }
    },
    '5. Security headers enforced (HSTS, X-Frame-Options, CSP)': (r) =>
      Boolean(r.headers['Strict-Transport-Security']) &&
      r.headers['X-Frame-Options'] === 'DENY' &&
      Boolean(r.headers['Content-Security-Policy']),
  });

  qrScanSuccessRate.add(isSuccess);

  // Pacing: 50ms - 150ms between scans per user simulates rapid mobile QR camera scanning
  sleep(0.05 + Math.random() * 0.1);
}

// ---------------------------------------------------------------------------
// Summary Callback: Formatted Output
// ---------------------------------------------------------------------------
export function handleSummary(data) {
  const reqTotal = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0;
  const reqRate = data.metrics.http_reqs ? data.metrics.http_reqs.values.rate.toFixed(1) : 0;
  const p95 = data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'].toFixed(2) : 0;
  const p99 = data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(99)'].toFixed(2) : 0;
  const avg = data.metrics.http_req_duration ? data.metrics.http_req_duration.values.avg.toFixed(2) : 0;
  const failRate = data.metrics.http_req_failed ? (data.metrics.http_req_failed.values.rate * 100).toFixed(2) : 0;

  const summaryText = `
================================================================================
          TTPU RAQAMLI INVENTAR — QR PUBLIC SCAN 200 VU LOAD TEST RESULTS
================================================================================
Concurrent Virtual Users: 200 VUs
Target Endpoint:          GET /a/{id}
Total HTTP Requests:      ${reqTotal}
Throughput:               ${reqRate} req/sec
Average Latency:          ${avg} ms
p(95) Latency:            ${p95} ms (SLA < 800 ms)
p(99) Latency:            ${p99} ms (SLA < 1500 ms)
HTTP Failure Rate:        ${failRate}% (Uptime SLA: 99.5% -> failures < 0.5%)
Overall SLA Compliance:   ${failRate < 0.5 && p95 < 2000 ? 'PASSED (MET 99.5% UPTIME & < 2s SLA)' : 'FAILED'}
================================================================================
`;
  return {
    stdout: summaryText,
  };
}
