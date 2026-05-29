import http from 'k6/http';
import { check } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
    stages: [
        { duration: '10s', target: 50 },
        { duration: '20s', target: 100 },
        { duration: '30s', target: 200 },  // Stress: 200 VUs
        { duration: '30s', target: 200 },  // Sustained stress
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(99)<2000'],
        http_req_failed: ['rate<0.15'],  // Hard cut da Rinha: > 15% = -3000
    },
};

const BASE_URL = 'http://localhost:9999';
const headers = { 'Content-Type': 'application/json' };

// Payload fixo para stress (remove overhead de geração)
const payload = JSON.stringify({
    id: 'stress-test',
    transaction: { amount: 5000, installments: 6, requested_at: '2026-03-12T12:00:00Z' },
    customer: { avg_amount: 1000, tx_count_24h: 10, known_merchants: [] },
    merchant: { id: 'm1', mcc: '5411', avg_amount: 5000 },
    terminal: { is_online: true, card_present: true, km_from_home: 500 },
    last_transaction: { timestamp: '2026-03-12T00:00:00Z', km_from_current: 500 },
});

export default function () {
    const res = http.post(`${BASE_URL}/fraud-score`, payload, { headers });
    check(res, { 'status 200': (r) => r.status === 200 });
}
