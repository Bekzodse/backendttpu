/**
 * TTPU "Raqamli Inventar" — Milestone 6.2: k6 Load Testing Script (load-test.js)
 *
 * Scenario Specification:
 * - Total Concurrent Users: 200 VUs
 *   1. qr_public_scan: 30 concurrent VUs constantly hitting GET /a/{id}
 *   2. authenticated_erp_users: 170 concurrent VUs executing typical ERP read/browse operations
 * - SLA / Thresholds:
 *   - Expected response time under 2 seconds (< 2000 ms) across 95th/99th percentiles
 *   - Error rate < 1%
 *
 * Usage:
 *   k6 run load-test.js
 *   k6 run -e BASE_URL=http://localhost:8080 -e QR_IDS=JBSWY3DPEB,KRSXG5BAON load-test.js
 */

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ---------------------------------------------------------------------------
// Custom Metrics
// ---------------------------------------------------------------------------
const qrScanDuration = new Trend('qr_scan_duration_ms', true);
const erpApiDuration = new Trend('erp_api_duration_ms', true);
const qrScanSuccessRate = new Rate('qr_scan_success_rate');
const qrScanUnder2sRate = new Rate('qr_scan_under_2s_rate');
const totalQrScans = new Counter('total_qr_scans');

// ---------------------------------------------------------------------------
// Environment Configuration
// ---------------------------------------------------------------------------
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TEST_DURATION = __ENV.DURATION || '2m';
const RAMP_UP = __ENV.RAMP_UP || '15s';
const RAMP_DOWN = __ENV.RAMP_DOWN || '10s';

// ---------------------------------------------------------------------------
// k6 Options: 200 Concurrent Users (30 QR Public Scan + 170 ERP Users)
// ---------------------------------------------------------------------------
export const options = {
  scenarios: {
    // 30 concurrent users constantly hitting the public QR scan endpoint (/a/{id})
    qr_public_scan: {
      executor: 'constant-vus',
      vus: 30,
      duration: TEST_DURATION,
      exec: 'qrPublicScanScenario',
      tags: { workload: 'public_qr_scan' },
    },

    // Remaining 170 concurrent users simulating authenticated university staff
    // (Total concurrent users = 30 + 170 = 200 VUs)
    authenticated_erp_users: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_UP, target: 170 },
        { duration: TEST_DURATION, target: 170 },
        { duration: RAMP_DOWN, target: 0 },
      ],
      exec: 'authenticatedErpScenario',
      tags: { workload: 'authenticated_erp' },
    },
  },

  thresholds: {
    // Global HTTP response time must stay under 2 seconds (2000 ms)
    http_req_duration: ['p(95)<2000', 'p(99)<2000'],

    // Strict SLA for the QR public scan endpoint (/a/{id}): under 2 seconds
    'http_req_duration{endpoint:qr_public_scan}': [
      'avg<500',
      'p(95)<1500',
      'p(99)<2000',
      'max<2000',
    ],
    'http_req_duration{workload:public_qr_scan}': ['p(95)<1500', 'p(99)<2000'],
    qr_scan_duration_ms: ['p(95)<1500', 'p(99)<2000', 'max<2000'],
    qr_scan_under_2s_rate: ['rate>0.99'],
    qr_scan_success_rate: ['rate>0.99'],

    // Authenticated ERP workload SLA: under 2 seconds
    'http_req_duration{workload:authenticated_erp}': ['p(95)<2000', 'p(99)<2000'],
    erp_api_duration_ms: ['p(95)<2000'],

    // Overall failure rate and functional check pass rate
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },

  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// ---------------------------------------------------------------------------
// Setup Phase: Runs once before VUs start to avoid triggering Bucket4j login
// rate limiting (10 req/min per IP on /api/v1/auth/login) and to discover or
// seed valid 10-character Base32 (A-Z2-7) QR codes.
// ---------------------------------------------------------------------------
export function setup() {
  const configuredQrIds = (__ENV.QR_IDS || '')
    .split(',')
    .map((id) => id.trim())
    .filter((id) => id.length > 0);

  let accessToken = __ENV.ACCESS_TOKEN || null;
  let qrIds = configuredQrIds.slice();
  let itemIds = [];

  // Attempt single login in setup() if credentials are provided and no token was pre-supplied
  if (!accessToken && __ENV.USERNAME && __ENV.PASSWORD) {
    const loginPayload = JSON.stringify({
      username: __ENV.USERNAME,
      password: __ENV.PASSWORD,
    });
    const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, loginPayload, {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'auth_login_setup' },
    });

    if (loginRes.status === 200) {
      try {
        const body = loginRes.json();
        accessToken = body.accessToken || null;
      } catch (_) {
        accessToken = null;
      }
    }
  }

  // Discover existing QR codes and asset UUIDs if we have an access token
  if (accessToken) {
    const assetsRes = http.get(`${BASE_URL}/api/v1/assets`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: 'application/json',
      },
      tags: { endpoint: 'assets_list_setup' },
    });

    if (assetsRes.status === 200) {
      try {
        const assets = assetsRes.json();
        if (Array.isArray(assets)) {
          for (const item of assets) {
            if (item.qrCode && qrIds.indexOf(item.qrCode) === -1) {
              qrIds.push(item.qrCode);
            }
            if (item.id) {
              itemIds.push(item.id);
            }
          }
        }
      } catch (_) {
        // Ignore JSON parse errors during setup discovery
      }
    }
  }

  // Fallback 10-character Base32 (A-Z2-7) QR IDs if none were discovered
  if (qrIds.length === 0) {
    qrIds = ['JBSWY3DPEB', 'KRSXG5BAON', 'MZXW6YTBOI', 'NBSWY3DPF4', 'ORSXG5BAMN'];
  }

  return {
    baseUrl: BASE_URL,
    qrIds,
    itemIds,
    accessToken,
  };
}

// ---------------------------------------------------------------------------
// Scenario 1: 30 Concurrent Users Constantly Hitting Public QR Scan (/a/{id})
// ---------------------------------------------------------------------------
export function qrPublicScanScenario(data) {
  const qrIds = data.qrIds;
  const qrId = qrIds[(__VU + __ITER) % qrIds.length];

  const response = http.get(`${data.baseUrl}/a/${ encodeURIComponent(qrId) }`, {
    headers: {
      Accept: 'application/json',
    },
    tags: {
      endpoint: 'qr_public_scan',
      name: 'GET /a/{id}',
    },
  });

  totalQrScans.add(1);
  qrScanDuration.add(response.timings.duration);

  const under2Seconds = response.timings.duration < 2000;
  qrScanUnder2sRate.add(under2Seconds);

  const passed = check(response, {
    'QR scan status is 200 OK': (r) => r.status === 200,
    'QR scan response time < 2000ms': (r) => r.timings.duration < 2000,
    'QR scan returns ownershipLabel "TTPU mulki"': (r) => {
      if (r.status !== 200) return false;
      try {
        const body = r.json();
        return (
          body.ownershipLabel === 'TTPU mulki' &&
          typeof body.inventoryNumber === 'string' &&
          typeof body.contactPhone === 'string'
        );
      } catch (_) {
        return false;
      }
    },
    'Security headers (HSTS, X-Frame-Options, CSP) present': (r) =>
      Boolean(r.headers['Strict-Transport-Security']) &&
      r.headers['X-Frame-Options'] === 'DENY' &&
      Boolean(r.headers['Content-Security-Policy']),
  });

  qrScanSuccessRate.add(passed);

  // Minimal pacing for constant QR scanner traffic
  sleep(0.1);
}

// ---------------------------------------------------------------------------
// Scenario 2: 170 Concurrent Users Executing Authenticated / General ERP Requests
// ---------------------------------------------------------------------------
export function authenticatedErpScenario(data) {
  group('ERP Concurrent User Operations', () => {
    if (data.accessToken) {
      const authHeaders = {
        Authorization: `Bearer ${data.accessToken}`,
        Accept: 'application/json',
      };

      // 1. List assets
      const listRes = http.get(`${data.baseUrl}/api/v1/assets`, {
        headers: authHeaders,
        tags: {
          endpoint: 'assets_list',
          name: 'GET /api/v1/assets',
        },
      });

      erpApiDuration.add(listRes.timings.duration);
      check(listRes, {
        'ERP list assets status is 200': (r) => r.status === 200,
        'ERP list assets response time < 2000ms': (r) => r.timings.duration < 2000,
      });

      // 2. Fetch specific asset card if available
      if (data.itemIds && data.itemIds.length > 0) {
        const itemId = data.itemIds[(__VU + __ITER) % data.itemIds.length];
        const detailRes = http.get(`${data.baseUrl}/api/v1/assets/${itemId}`, {
          headers: authHeaders,
          tags: {
            endpoint: 'asset_detail',
            name: 'GET /api/v1/assets/{id}',
          },
        });

        erpApiDuration.add(detailRes.timings.duration);
        check(detailRes, {
          'ERP asset detail status is 200': (r) => r.status === 200,
          'ERP asset detail response time < 2000ms': (r) => r.timings.duration < 2000,
        });
      }
    } else {
      // If running without JWT credentials, simulate public API / health + API v1 QR lookup
      const qrId = data.qrIds[(__VU + __ITER) % data.qrIds.length];
      const apiQrRes = http.get(`${data.baseUrl}/api/v1/a/${encodeURIComponent(qrId)}`, {
        headers: { Accept: 'application/json' },
        tags: {
          endpoint: 'api_v1_qr_scan',
          name: 'GET /api/v1/a/{id}',
        },
      });

      erpApiDuration.add(apiQrRes.timings.duration);
      check(apiQrRes, {
        'ERP /api/v1/a/{id} status is 200': (r) => r.status === 200,
        'ERP /api/v1/a/{id} response time < 2000ms': (r) => r.timings.duration < 2000,
      });
    }
  });

  // Realistic user think time between ERP actions (0.5s - 1.5s)
  sleep(0.5 + Math.random());
}
