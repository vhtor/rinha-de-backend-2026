import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
    stages: [
        { duration: '10s', target: 10 },   // Ramp up: 0 → 10 VUs
        { duration: '30s', target: 50 },   // Ramp up: 10 → 50 VUs
        { duration: '60s', target: 50 },   // Sustained: 50 VUs por 1 minuto
        { duration: '10s', target: 0 },    // Ramp down
    ],
    thresholds: {
        http_req_duration: [
            'p(95)<500',   // 95% das requests < 500ms
            'p(99)<2000',  // 99% das requests < 2000ms (hard cut da Rinha)
        ],
        http_req_failed: ['rate<0.01'],  // < 1% de falhas
    },
}

const BASE_URL = 'http://localhost:9999';
const headers = { 'Content-Type': 'application/json' };

// Pool de payloads variados para simular diversidade
function generatePayload() {
    const amount = Math.random() * 10000;
    const installments = randomIntBetween(1, 12);
    const hour = randomIntBetween(0, 23);
    const isOnline = Math.random() > 0.5
    const hasLastTx = Math.random() > 0.3

    const payload = {
        id: `load-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        transaction: {
            amount: amount,
            installments: installments,
            requested_at: `2026-03-11T${String(hour).padStart(2, '0')}:30:00Z`,
        },
        customer: {
            avg_amount: amount * (0.5 + Math.random()),
            tx_count_24h: randomIntBetween(0, 20),
            known_merchants: isOnline ? [] : ['merchant-1'],
        },
        merchant: {
            id: isOnline ? 'merchant-unknown' : 'merchant-1',
            mcc: ['5411', '7995', '5912', '4814'][randomIntBetween(0, 3)],
            avg_amount: Math.random() * 10000,
        },
        terminal: {
            is_online: isOnline,
            card_present: !isOnline,
            km_from_home: Math.random() * 1000,
        },
        last_transaction: hasLastTx ? {
            timestamp: '2026-03-11T10:00:00Z',
            km_from_current: Math.random() * 1000,
        } : null,
    };

    return JSON.stringify(payload);
}

export default function () {
    const res = http.post(`${BASE_URL}/fraud-score`, generatePayload(), { headers });

    check(res, {
        'status is 200': (r) => r.status === 200,
        'has transaction_id': (r) => {
            try { return JSON.parse(r.body).transaction_id !== undefined; }
            catch { return false }
        }
    });

    sleep(0.1)
}
